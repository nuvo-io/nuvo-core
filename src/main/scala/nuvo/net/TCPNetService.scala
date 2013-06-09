package nuvo.net

import java.net._
import java.nio.channels._
import nuvo.nio.prelude._
import nuvo.nio.{BufferAllocator, ByteOrder, RawBuffer}
import nuvo.concurrent.SelectDispatchPool
import nuvo.runtime.Config._
import nuvo.net.prelude._
import java.io.IOException

class TCPNetService(val channel: SocketChannel, l: Locator, e: (RawBuffer, TCPNetService) => Unit)(implicit bufAllocator: BufferAllocator) extends NetService(l) {


  private val allocator: () => RawBuffer = () => {
    bufAllocator.allocateDirect(Networking.defaultBufferSize)
  }

  private val selector: (RawBuffer) => RawBuffer= (buf: RawBuffer) => {
    try {
      //print("<")
      tcpNuvoSelector(channel, buf)
      ///print(">")
      buf
    } catch {
      case cce: ClosedChannelException => {
        println("Catched: ClosedChannelException ")
        this.stop()
        buf
      }
      case ace: AsynchronousCloseException => {
        println("Catched: AsynchronousCloseException ")
        this.stop()
        buf
      }
      case cie: ClosedByInterruptException => {
        println("Catched: ClosedByInterruptException")
        this.stop()
        buf
      }
      case ioe: IOException => {
        println("Catched: IOException ")
        this.stop()
        buf
      }
    }
  }

  private val executor = (buf: RawBuffer) => e(buf, this)

  private lazy val executorPool =
    SelectDispatchPool[RawBuffer, RawBuffer](1, allocator, selector, executor)

  def start() { executorPool start() }

  def stop() {
    println(s"Stopping TCPNetService: $this")
    executorPool stop()
    channel.close()
  }

}
