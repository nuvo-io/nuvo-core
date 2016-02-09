package io.nuvo.net

import java.io.IOException
import java.net.{SocketAddress, InetAddress, InetSocketAddress}
import java.nio.channels.{ServerSocketChannel, Selector, SocketChannel, SelectionKey}
import java.util.concurrent.atomic.{AtomicReference, AtomicLong, AtomicBoolean}
import io.nuvo.nio.RawBuffer
import io.nuvo.nio.prelude._
import io.nuvo.runtime.Config._
import io.nuvo.concurrent.synchronizers._


/**
 * This class uses a thread to read messages from connected channels
 * and one to write into connected channels.
 *
 * @param endpoints the list of endpoints at which this message pump receive data
 * @param reader reads the data out of the channel
 * @param bufSize the size of the buffer used by this message pump.
 * @param consumer  consumes a message from the pump and gives back the buffer to be used for the next I/O operation.
 */
class TCPMessagePump(val endpoints: List[Endpoint],
                     reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int,
                     consumer: MessagePumpMsg => RawBuffer) extends MessagePump {
  require {
    endpoints.map(_.proto == Protocol.tcp).foldRight(true)(_ && _)
  }

  def this(endpoint: Endpoint,
           reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int,
           consumer: MessagePumpMsg => RawBuffer) = this(List(endpoint), reader, bufSize, consumer)

  private val connectionMapRef = new AtomicReference(Map[SocketAddress, (SocketChannel, AtomicBoolean)]())
  private val acceptorMapRef = new AtomicReference(Map[SocketAddress, ServerSocketChannel]())

  val rselector = Selector.open()
  val wselector = Selector.open()


  private val rdispatcher = new Runnable() {
    def run() {
      var buf = RawBuffer.allocateDirect(bufSize)

      endpoints.foreach { endpoint =>
        val addr = endpoint.socketAddress

        val acceptor= ServerSocketChannel.open()
        acceptor.bind(addr)
        acceptor.configureBlocking(false)
        val k = acceptor.register(rselector, SelectionKey.OP_ACCEPT)
        k.attach(acceptor)
        compareAndSet(acceptorMapRef) { acceptorMap => acceptorMap + (addr -> acceptor) }
      }

      var interrupted = false

      while (!interrupted) {
        var k: SelectionKey = null
        try {
          rselector.select()
          val keys =  rselector.selectedKeys()
          val iterator = keys.iterator()

          while (iterator.hasNext) {
            k = iterator.next()
            iterator.remove()

            if (k.isReadable) {
              val remoteAddress = k.attachment().asInstanceOf[SocketAddress]
              val d = reader(k, buf)
              buf = consumer(DataAvailable(d, remoteAddress, TCPMessagePump.this))
            }
            else if (k.isAcceptable) {
              val acceptor = k.attachment().asInstanceOf[ServerSocketChannel]
              val channel = acceptor.accept()
              channel.configureBlocking(false)
              channel.socket().setTcpNoDelay(Networking.Socket.TCP_NO_DELAY)
              channel.socket().setSendBufferSize(Networking.Socket.SendBufSize)
              channel.socket().setPerformancePreferences(
                Networking.Socket.Performance._1,
                Networking.Socket.Performance._2,
                Networking.Socket.Performance._3)

              val remoteAddress = channel.getRemoteAddress()
              channel.register(rselector, SelectionKey.OP_READ, remoteAddress)
              val cg = (channel, new AtomicBoolean(true))
              compareAndSet(connectionMapRef) { connectionMap => connectionMap + (remoteAddress -> cg) }

              log.debug("> Accepted Connection from: " + channel.getRemoteAddress())
            }
          }
        } catch {
          case ie: InterruptedException => {
            interrupted = true
            compareAndSet(acceptorMapRef) { acceptorMap =>
              acceptorMap.foreach(_._2.close())
              Map[SocketAddress, ServerSocketChannel]()
            }
            consumer(ServiceStopped)
          }
          case ioe: IOException => {
            if (k != null) {
              val remoteAddress = k.attachment().asInstanceOf[SocketAddress]
              k.cancel()
              k.channel().close()
              compareAndSet(connectionMapRef) { connectionMap => connectionMap - remoteAddress }
              consumer(ConnectionClosed(remoteAddress))
              log.warning(s"Closing connection to: $remoteAddress ")
              log.warning(s"Due to error:\n\t$ioe")
            }
            else {
              log.error(ioe.toString)
            }
          }
        }
      }
    }
  }
  private val rdt = new Thread(rdispatcher)

  def connections = connectionMapRef.get

  override def start() {
    rdt.start()
  }

  override def stop() {
    compareAndSet(connectionMapRef) {connectionMap =>
      connectionMap.foreach(e => {
        val channel = e._2._1
        val k = channel.keyFor(rselector)
        k.cancel()
        channel.close()
      } )
      Map[SocketAddress, (SocketChannel, AtomicBoolean)]()
    }

    rdt.interrupt()
    rdt.join()
    rselector.close()
    wselector.close()

  }

  /** Writes the content of the buffer on the socket channel provided.
    * Plase notice that this write operation is not atomic! That means 
    * that concurrent call to writeBuffer may cause interleaving of the
    * buffer data over the channel. 
    *  
    * @param buf: the buffer whose content is to be written on the channel
    * @param channel: the channel over which the buffer data has to be written.
    *  
    */
  private def writeBuffer(buf: RawBuffer, channel: SocketChannel): Unit = {
    // @NOTE: The current implementation of this method assume that 
    // there are never two concurrent write on the same channel. If this
    // assumtion should be violated then we may end-up with data from
    // different in-buffers being interleaved. This would clearly happ
    // only in the case in which the buffer is written across multiple write
    // operations.
    
    var k: Option[SelectionKey] = None
    var selector: Option[Selector] = None
    do {
      selector.map(_.select())
      channel.write(buf)
      if (buf.position != buf.limit && k == None) {
        selector = Some(Selector.open())
        selector.map { s =>
          k = Some(channel.register(s, SelectionKey.OP_WRITE))
        }
      }
    } while (buf.position != buf.limit)
    k map { _.cancel() }
    selector map { _.close() }
  }

  /**
    * This operation writes the content of the provided buffer on the given channel.
    * Concurrent writes over the same channel are serialized to ensure that 
    * the no interleaving is possible. 
    * 
    * @param buf: the buffer whose content has to be written on the channel
    * @remoteAddress: the address at which the content has to be sent.
    */
  override def writeTo(buf: RawBuffer, remoteAddress: SocketAddress): Unit = {
    val connection = connectionMapRef.get().get(remoteAddress)
    connection.map { c =>
      val someChannel = c._1
      val guard = c._2

       guard.synchronized {
         while (guard.compareAndSet(true, false) == false) {
           guard.wait()
         }
         writeBuffer(buf, someChannel)
         guard.compareAndSet(false, true)
         guard.notifyAll()
       }
    }.orElse{
      log.warning(s"The remote address $remoteAddress does not match any connection.")
      None
    }
  }

  override def close(remoteAddress: SocketAddress) {
    compareAndSet(connectionMapRef) { connectionMap =>
      connectionMap.get(remoteAddress) match {
        case Some((channel, guard)) => {
          val k = channel.keyFor(rselector)
          k.cancel()
          channel.close()
          connectionMap - remoteAddress
        }
        case _ => {
          log.warning(s"The remote address $remoteAddress does not match any connection")
          connectionMap
        }
      }
    }
  }

  def resolveEndpoint(remoteAddress: SocketAddress): Option[SocketAddress] = {
    connectionMapRef.get().get(remoteAddress) map { _._1.getRemoteAddress}
  }

  override def add(endpoint: Endpoint): Unit = {
    val addr = endpoint.socketAddress

    val acceptor= ServerSocketChannel.open()
    acceptor.bind(addr)
    acceptor.configureBlocking(false)
    val k = acceptor.register(rselector, SelectionKey.OP_ACCEPT)
    k.attach(acceptor)
    compareAndSet(acceptorMapRef) { acceptorMap => acceptorMap + (addr -> acceptor) }
  }

  override def close(endpoint: Endpoint): Unit =
    close(new InetSocketAddress(endpoint.address, endpoint.port))

  /**
   * Write the content of a buffer to all the endpoint currently connected.
   *
   * @param buf the buffer to be written.
   */
  override def writeTo(buf: RawBuffer): Unit = {
    val connectionMap = connectionMapRef.get()
    connectionMap.foreach(e => writeBuffer(buf, e._2._1))
  }

}
