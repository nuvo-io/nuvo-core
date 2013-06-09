package nuvo.net

import java.nio.channels._
import java.net._
import nuvo.runtime.Config._
import nuvo.nio.prelude._
import nuvo.nio.RawBuffer

class UDPNetLink(val locator: Locator) extends NetLink {

  private def initChannel() = {
    import scala.collection.JavaConversions._

    val addr = InetAddress.getByName(locator.address)
    val channel: DatagramChannel = addr match {
      case i4: Inet4Address => DatagramChannel.open(StandardProtocolFamily.INET)
      case i6: Inet6Address => DatagramChannel.open(StandardProtocolFamily.INET6)
      case _ => {
        log.error("Unknown UDP address family")
        throw new RuntimeException("Unknown UDP address family")
      }
    }
    if (addr.isMulticastAddress) {
      val ifaces = NetworkInterface.getNetworkInterfaces
      val iface = ifaces find  (i => i.isUp && i.supportsMulticast())
      iface match {
        case Some(i) => {
          channel.setOption[java.lang.Boolean](StandardSocketOptions.SO_REUSEADDR, true)
            .bind(new InetSocketAddress(locator.port))
            .setOption(StandardSocketOptions.IP_MULTICAST_IF, i)
            .join(addr, i)

          channel.connect(new InetSocketAddress(addr, locator.port))
        }
        case None => {
          log.error("Failed to find an intefarce that supports multicast")
        }
      }
    }
    else {
      val sockAddr = new InetSocketAddress(addr, locator.port)
      channel.connect(sockAddr)
    }
    channel
  }

  override val channel: DatagramChannel =  initChannel()

  @inline def write(data: RawBuffer) =  channel.write(data)
  @inline def read(data: RawBuffer) = channel.read(data)

}
