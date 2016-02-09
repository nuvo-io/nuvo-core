package io.nuvo.net

import java.net._
import java.nio.channels.DatagramChannel
import io.nuvo.concurrent.SelectDispatchPool
import io.nuvo.runtime.Config._
import io.nuvo.nio.prelude._
import io.nuvo.nio.{BufferAllocator, RawBuffer, ByteOrder}
import io.nuvo.runtime.CoreRuntime


// TODO: Make the allocator an implicit val


class UDPNetService(endpoint: Endpoint, e: (RawBuffer, NetService) => Any)(implicit bufAllocator: BufferAllocator) extends NetService(endpoint) {

  require {
    endpoint.proto == Protocol.udp
  }
  val (channel, cleanup) = createChannel(endpoint)

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

  private def createChannel(endpoint: Endpoint) = {

    if (endpoint.isMulticast) {
      val ni = NetworkInterface.getByName(Networking.defaultNIC);

      val channel = DatagramChannel.open(endpoint.protoFamily)
      channel.setOption(StandardSocketOptions.SO_REUSEADDR, Boolean.box(true))
      channel bind new InetSocketAddress(endpoint.port)
      channel setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);
      val key = channel.join(endpoint.address, ni)
      (channel, () => {
        key.drop()
        channel.close()
      })
    }
    else {
      CoreRuntime.log.debug("Opening DatagramChannel")
      val channel = DatagramChannel.open(endpoint.protoFamily)
      CoreRuntime.log.debug("Binding DatagramChannel on " + endpoint.socketAddress)
      channel.bind(endpoint.socketAddress)
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
