package io.nuvo.net

import java.nio.channels._
import java.net._
import io.nuvo.runtime.Config._
import io.nuvo.nio.prelude._
import io.nuvo.nio.RawBuffer

class UDPNetLink(val endpoint: Endpoint) extends NetLink {

  private def initChannel() = {
    import scala.collection.JavaConversions._

    val addr = endpoint.socketAddress.getAddress
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
            .bind(new InetSocketAddress(endpoint.port))
            .setOption(StandardSocketOptions.IP_MULTICAST_IF, i)
            .join(addr, i)

          channel.connect(endpoint.socketAddress)
        }
        case None => {
          log.error("Failed to find an intefarce that supports multicast")
        }
      }
    }
    else {
      channel.connect(endpoint.socketAddress)
    }
    channel
  }

  override val channel: DatagramChannel =  initChannel()

  def write(data: RawBuffer) =  channel.write(data)
  def read(data: RawBuffer) = channel.read(data)

}
