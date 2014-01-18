package nuvo.net

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress}
import java.nio.channels.{ServerSocketChannel, Selector, SocketChannel, SelectionKey}
import java.util.concurrent.atomic.{AtomicReference, AtomicLong}
import nuvo.nio.RawBuffer
import nuvo.nio.prelude._
import nuvo.runtime.Config._
import nuvo.concurrent.synchronizers._


/**
 * This class uses a thread to read messages from connected channels and one to write into connected channels.
 *
 * @param locator the locator at which this message pump will be instantiated
 * @param reader reads the data out of the channel
 * @param bufSize the size of the buffer used by this message pump.
 * @param consumer  consumes a message from the pump and gives back the buffer to be used for the next I/O operation.
 */
class TCPMessagePump(val locator: Locator, reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int, consumer: MessagePumpMessage => RawBuffer) extends MessagePump {
  private val connectionMapRef = new AtomicReference(Map[Long, SocketChannel]())
  // @volatile private var connectionMap = Map[Long, SocketChannel]()
  val rselector = Selector.open()
  val wselector = Selector.open()
  private val counter = new AtomicLong(0)

  private val rdispatcher = new Runnable() {
    def run() {
      var buf = RawBuffer.allocateDirect(bufSize)

      val addr =
        new InetSocketAddress(InetAddress.getByName(locator.address), locator.port)

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
              val cid = k.attachment().asInstanceOf[Long]
              buf = consumer(DataAvailable(reader(k, buf), cid, TCPMessagePump.this))
            }
            else if (k.isAcceptable) {
              val channel = acceptor.accept()
              channel.configureBlocking(false)
              channel.socket().setTcpNoDelay(Networking.Socket.TCP_NO_DELAY)
              channel.socket().setSendBufferSize(Networking.Socket.SendBufSize)
              channel.socket().setPerformancePreferences(Networking.Socket.Performance._1, Networking.Socket.Performance._2, Networking.Socket.Performance._3)

              val cid = counter.getAndIncrement()
              channel.register(rselector, SelectionKey.OP_READ, cid)

              compareAndSet(connectionMapRef) { connectionMap => connectionMap + (cid -> channel) }

              log.debug("> Accepted Connection from: " + channel.getRemoteAddress() + s" [$cid]")
            }
          }
        } catch {
          case ie: InterruptedException => {
            interrupted = true
            consumer(ServiceStopped)
          }
          case ioe: IOException => {
            if (k != null) {
              val cid = k.attachment().asInstanceOf[Long]
              k.cancel()
              k.channel().close()
              compareAndSet(connectionMapRef) { connectionMap => connectionMap - cid }
              consumer(ConnectionClosed(cid))
              log.warning(s"Closing connection: $cid")
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

  def writeTo(buf: RawBuffer, cid: Long) {
    connectionMapRef.get().get(cid) map { channel =>
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
      log.warning(s"Unknown channel ID: $cid")
    }
  }

  def close(cid: Long) {
    connectionMapRef.get().get(cid) map { channel =>
      val k = channel.keyFor(rselector)
      k.cancel()
      channel.close()
    } getOrElse {
      log.warning(s"Unknown channel ID: $cid")
    }
  }

}