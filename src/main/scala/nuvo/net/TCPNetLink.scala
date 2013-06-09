package nuvo.net

import java.net.{InetSocketAddress, InetAddress}
import java.nio.channels.SocketChannel
import nuvo.nio.prelude._
import nuvo.nio.RawBuffer

class TCPNetLink(val locator: Locator) extends NetLink {

  private def initChannel() = {
    val addr = new InetSocketAddress(InetAddress.getByName(locator.address), locator.port)
    SocketChannel.open(addr)
  }

  override val channel: SocketChannel =  initChannel()

  @inline def write(data: RawBuffer) =  channel.write(data)
  @inline def read(data: RawBuffer) = channel.read(data)
}

