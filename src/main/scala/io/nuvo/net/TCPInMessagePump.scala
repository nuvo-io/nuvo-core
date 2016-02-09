package io.nuvo.net

import java.io.IOException
import java.net.{SocketAddress, InetAddress, InetSocketAddress}
import java.nio.channels.{ServerSocketChannel, Selector, SocketChannel, SelectionKey}
import java.util.concurrent.atomic.{AtomicReference, AtomicLong}
import io.nuvo.nio.RawBuffer
import io.nuvo.nio.prelude._
import io.nuvo.runtime.Config._
import io.nuvo.concurrent.synchronizers._


/**
 * This class uses a thread to read messages from connected channels and one to write into connected channels.
 *
 * @param endpoints the list of endpoints at which this message pump receive data
 * @param reader reads the data out of the channel
 * @param bufSize the size of the buffer used by this message pump.
 * @param consumer  consumes a message from the pump and gives back the buffer to be used for the next I/O operation.
 */

class TCPInMessagePump(val endpoints: List[Endpoint],
                       reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int,
                       consumer: MessagePumpMsg => RawBuffer) extends InMessagePump  {

  require {
    endpoints.map(_.proto == Protocol.tcp).foldRight(true)(_ && _)
  }

  def this(endpoint: Endpoint,
           reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int,
           consumer: MessagePumpMsg => RawBuffer) = this(List(endpoint), reader, bufSize, consumer)

  private val connectionMapRef = new AtomicReference(Map[SocketAddress, SocketChannel]())

  private val acceptorMapRef = new AtomicReference(Map[SocketAddress, ServerSocketChannel]())

  val selector = Selector.open()

  private val dispatcher = new Runnable() {
    def run() {
      var buf = RawBuffer.allocateDirect(bufSize)

      endpoints.foreach { endpoint =>
        val addr = endpoint.socketAddress

        val acceptor = ServerSocketChannel.open()
        acceptor.bind(addr)
        acceptor.configureBlocking(false)
        val k = acceptor.register(selector, SelectionKey.OP_ACCEPT)
        k.attach(acceptor)
        compareAndSet(acceptorMapRef) { acceptorMap => acceptorMap + (addr -> acceptor) }
      }

      var interrupted = false

      while (!interrupted) {
        var k: SelectionKey = null
        try {
          selector.select()
          val keys =  selector.selectedKeys()
          val iterator = keys.iterator()

          while (iterator.hasNext) {
            k = iterator.next()
            iterator.remove()

            if (k.isReadable) {
              val remoteAddress = k.attachment().asInstanceOf[SocketAddress]
              buf = consumer(DataAvailable(reader(k, buf), remoteAddress, TCPInMessagePump.this))
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
              channel.register(selector, SelectionKey.OP_READ, remoteAddress)

              compareAndSet(connectionMapRef) { connectionMap => connectionMap + (remoteAddress -> channel) }

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
  private val rdt = new Thread(dispatcher)

  def connections = connectionMapRef.get

  override def start() {
    rdt.start()
  }

  override def stop() {
    compareAndSet(connectionMapRef) {connectionMap =>
      connectionMap.foreach(e => {
        val channel = e._2
        val k = channel.keyFor(selector)
        k.cancel()
        channel.close()
      } )
      Map[SocketAddress, SocketChannel]()
    }

    rdt.interrupt()
    rdt.join()
    selector.close()
  }

  override def close(addr: SocketAddress): Unit = {
    compareAndSet(connectionMapRef) { connectionMap =>
      connectionMap.get(addr) match {
        case Some(channel) => {
          val k = channel.keyFor(selector)
          k.cancel()
          channel.close()
          connectionMap - addr
        }
        case _ => {
          log.warning(s"The remote address $addr does not match any connection")
          connectionMap
        }
      }
    }
  }
  override def close(endpoint: Endpoint): Unit = {
    val remoteAddress = new InetSocketAddress(endpoint.address, endpoint.port)
    close(remoteAddress)
  }


  def add(endpoint: Endpoint): Unit = {
    val addr = endpoint.socketAddress

    val acceptor= ServerSocketChannel.open()
    acceptor.bind(addr)
    acceptor.configureBlocking(false)
    val k = acceptor.register(selector, SelectionKey.OP_ACCEPT)
    k.attach(acceptor)
    compareAndSet(acceptorMapRef) { acceptorMap => acceptorMap + (addr -> acceptor) }
  }


}
