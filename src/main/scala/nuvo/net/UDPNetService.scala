package nuvo.net

import java.net._
import java.nio.channels.DatagramChannel
import nuvo.concurrent.SelectDispatchPool
import nuvo.runtime.Config._
import nuvo.nio.prelude._
import nuvo.nio.{BufferAllocator, RawBuffer, ByteOrder}


// TODO: Make the allocator an implicit val


class UDPNetService(l: Locator, e: (RawBuffer, NetService) => Unit)(implicit bufAllocator: BufferAllocator) extends NetService(l) {

  require {
    l.transport == "udp"
  }
  val (channel, cleanup) = createChannel(l)

  private val allocator: () => RawBuffer = () => {
    bufAllocator.allocate(Networking.defaultBufferSize)
  }

  private val selector: (RawBuffer) => RawBuffer= (buf: RawBuffer) => {
    buf.clear()
    channel.receive(buf)
    buf.flip()
    buf
  }
  private val executor = (buf: RawBuffer) => e(buf, this)

  private lazy val executorPool =
    SelectDispatchPool[RawBuffer, RawBuffer](allocator, selector, executor)

  private def createChannel(locator: Locator) = {
    val addr = InetAddress.getByName(l.address)
    if (addr.isMulticastAddress) {

      val ni = NetworkInterface.getByName(Networking.defaultNIC);
      val protoFamily =
        if (locator.address.length > 15) StandardProtocolFamily.INET6
        else StandardProtocolFamily.INET

      val channel = DatagramChannel.open(protoFamily)
      channel.setOption(StandardSocketOptions.SO_REUSEADDR, Boolean.box(true))
      channel bind new InetSocketAddress(locator.port)
      channel setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);
      val key = channel.join(addr, ni)
      (channel, () => {
        key.drop()
        channel.close()
      })
    }
    else {
      val sockAddr =
        new InetSocketAddress(addr, l.port)
      val channel = DatagramChannel.open()
      channel.bind(sockAddr)

      // TODO: Set socket options

      (channel,
        () => {
          channel.close()
        }
      )
    }
  }

  def start() = executorPool start()

  def stop() = {
    executorPool stop()
    cleanup()
  }

}
