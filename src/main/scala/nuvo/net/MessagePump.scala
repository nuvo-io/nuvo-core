package nuvo.net

import java.nio.channels.{ServerSocketChannel, Selector, SocketChannel, SelectionKey}
import nuvo.nio.RawBuffer
import java.net.{InetAddress, InetSocketAddress}
import nuvo.runtime.Config._
import nuvo.nio.prelude._
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

abstract class MessagePump {
  def writeTo(buf: RawBuffer, connectionID: Long)
  def start()
  def stop()
  def close(connectionId: Long)
}

case class MessagePayload(buf: RawBuffer, cid: Long, mp: MessagePump)

/**
 * This class uses a thread to read messages from connected channels and one to write into connected channels.
 *
 * @param locator
 * @param reader
 * @param consumer
 */
class TCPMessagePump(val locator: Locator, reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int, consumer: MessagePayload => Any) extends MessagePump {
  @volatile private var connectionMap = Map[Long, SocketChannel]()
  val rselector = Selector.open()
  val wselector = Selector.open()
  private val counter = new AtomicLong(0)

  private val rdispatcher = new Runnable() {
    def run() {
      val buf = RawBuffer.allocateDirect(bufSize)

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
              consumer(MessagePayload(reader(k, buf), cid, TCPMessagePump.this))
            }
            else if (k.isAcceptable) {
              val channel = acceptor.accept()
              channel.configureBlocking(false)
              channel.socket().setTcpNoDelay(Networking.Socket.TCP_NO_DELAY)
              channel.socket().setSendBufferSize(Networking.Socket.SendBufSize)
              channel.socket().setPerformancePreferences(Networking.Socket.Performance._1, Networking.Socket.Performance._2, Networking.Socket.Performance._3)

              val cid = counter.getAndIncrement()
              channel.register(rselector, SelectionKey.OP_READ, cid)
              connectionMap = connectionMap + (cid -> channel)

              log.debug("> Accepted Connection from: " + channel.getRemoteAddress() + s" [$cid]")
            }
          }
        } catch {
          case ie: InterruptedException => interrupted = true
          case ioe: IOException => {
            if (k != null) {
              val cid = k.attachment().asInstanceOf[Long]
              k.cancel()
              k.channel().close()
              connectionMap = connectionMap - cid
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

  def connections = connectionMap

  def start() {
    rdt.start()
  }
  def stop() {
    connectionMap.foreach { e => close(e._1) }
    rdt.interrupt()
    rdt.join()

  }

  def writeTo(buf: RawBuffer, cid: Long) {
    connectionMap.get(cid) map { channel =>
      do {
        channel.write(buf)
        if (buf.position != buf.limit) {
          log.warning(s"Socket Buffer are full, you may need to increase size")
          val k = channel.register(wselector, SelectionKey.OP_WRITE)
          wselector.wait()
          k.cancel()
        }
      } while (buf.position != buf.limit)
    } // getOrElse {
//      log.warning(s"Unknown channel ID: $cid")
//    }
  }

  def close(cid: Long) {
    connectionMap.get(cid) map { channel =>
        val k = channel.keyFor(rselector)
        k.cancel()
        channel.close()
    } getOrElse {
      log.warning(s"Unknown channel ID: $cid")
    }
  }

}