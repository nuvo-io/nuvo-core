package nuvo.net

import java.io.IOException
import java.net.{SocketAddress, InetAddress, InetSocketAddress}
import java.nio.channels.{ServerSocketChannel, Selector, SocketChannel, SelectionKey}
import java.util.concurrent.atomic.{AtomicReference, AtomicLong}
import nuvo.nio.RawBuffer
import nuvo.nio.prelude._
import nuvo.runtime.Config._
import nuvo.concurrent.synchronizers._


/**
 * This class uses a thread to read messages from connected channels and one to write into connected channels.
 *
 * @param endpoint the endpoint at which this message pump will be instantiated
 * @param reader reads the data out of the channel
 * @param bufSize the size of the buffer used by this message pump.
 * @param consumer  consumes a message from the pump and gives back the buffer to be used for the next I/O operation.
 */
class TCPMessagePump(val endpoint: Endpoint,
                     reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int,
                     consumer: MessagePumpMessage => RawBuffer) extends MessagePump {
  require {
    endpoint.proto == Protocol.tcp
  }
  private val connectionMapRef = new AtomicReference(Map[SocketAddress, SocketChannel]())
  val rselector = Selector.open()
  val wselector = Selector.open()
  private val counter = new AtomicLong(0)

  private val rdispatcher = new Runnable() {
    def run() {
      var buf = RawBuffer.allocateDirect(bufSize)

      val addr = endpoint.socketAddress

      val acceptor= ServerSocketChannel.open()
      acceptor.bind(addr)
      acceptor.configureBlocking(false)
      acceptor.register(rselector, SelectionKey.OP_ACCEPT)

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
              buf = consumer(TCPDataAvailable(reader(k, buf), remoteAddress, TCPMessagePump.this))
            }
            else if (k.isAcceptable) {
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

              compareAndSet(connectionMapRef) { connectionMap => connectionMap + (remoteAddress -> channel) }

              log.debug("> Accepted Connection from: " + channel.getRemoteAddress())
            }
          }
        } catch {
          case ie: InterruptedException => {
            interrupted = true
            acceptor.close()
            consumer(ServiceStopped)
          }
          case ioe: IOException => {
            if (k != null) {
              val remoteAddress = k.attachment().asInstanceOf[SocketAddress]
              k.cancel()
              k.channel().close()
              compareAndSet(connectionMapRef) { connectionMap => connectionMap - remoteAddress }
              consumer(TCPConnectionClosed(remoteAddress))
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

  def start() {
    rdt.start()
  }
  def stop() {
    connectionMapRef.get().foreach { e => close(e._1) }
    rdt.interrupt()
    rdt.join()
    rselector.close()
    wselector.close()

  }

  def writeTo(buf: RawBuffer, remoteAddress: SocketAddress) {
    connectionMapRef.get().get(remoteAddress) map { channel =>
      var k: Option[SelectionKey] = None
      var selector: Option[Selector] = None
      do {
        selector.map(_.select())
        channel.write(buf)
        if (buf.position != buf.limit && k == None) {
          log.warning(s"Socket Buffer are full, you may need to increase size")
          selector = Some(Selector.open())
          selector.map { s =>
            k = Some(channel.register(s, SelectionKey.OP_WRITE))
          }
        }
      } while (buf.position != buf.limit)
      k map { _.cancel() }
      selector map { _.close() }

    } getOrElse {
      log.warning(s"The remote address $remoteAddress does not match any connection")
    }
  }

  def close(remoteAddress: SocketAddress) {
    connectionMapRef.get().get(remoteAddress) map { channel =>
      val k = channel.keyFor(rselector)
      k.cancel()
      channel.close()
    } getOrElse {
      log.warning(s"The remote address $remoteAddress does not match any connection")
    }
  }

  def resolveEndpoint(remoteAddress: SocketAddress): Option[SocketAddress] = {
    connectionMapRef.get().get(remoteAddress) map { _.getRemoteAddress}
  }
}