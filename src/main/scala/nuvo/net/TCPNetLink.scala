package nuvo.net

import java.net.{InetSocketAddress, InetAddress}
import java.nio.channels.SocketChannel
import nuvo.nio.prelude._
import nuvo.nio.RawBuffer
import nuvo.runtime.Config.Networking

class TCPNetLink(val locator: Locator) extends NetLink {

  private def initChannel() = {
    val addr = new InetSocketAddress(InetAddress.getByName(locator.address), locator.port)
    val ch = SocketChannel.open(addr)
    ch.socket().setTcpNoDelay(Networking.Socket.TCP_NO_DELAY)
    ch.socket().setPerformancePreferences(Networking.Socket.Performance._1, Networking.Socket.Performance._2, Networking.Socket.Performance._3)
    ch
  }

  override val channel: SocketChannel =  initChannel()

  @inline def write(data: RawBuffer) =  channel.write(data)
  @inline def read(data: RawBuffer) = channel.read(data)
}

