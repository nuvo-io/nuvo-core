package io.nuvo.net

import java.net._
import java.nio.channels._
import io.nuvo.nio.prelude._
import io.nuvo.nio.{BufferAllocator, ByteOrder, RawBuffer}
import io.nuvo.concurrent.SelectDispatchPool
import io.nuvo.runtime.Config._
import io.nuvo.net.prelude._
import java.io.IOException

class TCPNetService(val channel: SocketChannel, endpoint: Endpoint, e: (RawBuffer, TCPNetService) => Any)(implicit bufAllocator: BufferAllocator) extends NetService(endpoint) {


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
