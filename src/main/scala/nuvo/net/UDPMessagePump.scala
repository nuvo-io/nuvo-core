package nuvo.net

import nuvo.runtime.Config._
import nuvo.nio.prelude._
import nuvo.nio.RawBuffer
import java.nio.channels.{DatagramChannel, Selector, SelectionKey}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.net._
import nuvo.runtime.Config.Networking
import scala.Predef._
import scala.Some
import java.io.IOException

/**
 * This class uses a thread to read messages from connected channels and one to write into connected channels.
 *
 * @param endpoint the endpoint at which this message pump will be instantiated
 * @param bufSize the size of the buffer used by this message pump.
 * @param consumer  consumes a message from the pump and gives back the buffer to be used for the next I/O operation.
 */
class UDPMessagePump (val endpoint: Endpoint,  bufSize: Int, consumer: MessagePumpMessage => RawBuffer) extends MessagePump {
  require {
    endpoint.proto == "udp"
  }
  var sch: Option[DatagramChannel] = None

  private val rdispatcher = new Runnable() {
    def run() {
      var buf = RawBuffer.allocateDirect(bufSize)

      val (channel, cleanupFun) = if (endpoint.address.isMulticastAddress) {

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
        val sockAddr =
          new InetSocketAddress(endpoint.address, endpoint.port)
        val channel = DatagramChannel.open()
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, Boolean.box(true))
        channel.bind(sockAddr)
        log.log("Channel bound at: " + channel.socket().getLocalAddress);

        // TODO: Set socket options
        (channel,
          () => {
            channel.close()
          })
      }
      sch = Some(channel)

      var interrupted = false

      while (!interrupted) {
        var k: SelectionKey = null
        try {
          val peerAddr = channel.receive(buf)
          buf = consumer(UDPDataAvailable(buf, peerAddr, UDPMessagePump.this))
          buf.clear()
        } catch {
          case ie: InterruptedException => {
            interrupted = true
            cleanupFun()
            consumer(ServiceStopped)
          }
          case ioe: IOException => {
            log.error(ioe.toString)
          }
        }
      }
    }
  }
  private val rdt = new Thread(rdispatcher)

  def start() {
    rdt.start()
  }
  def stop() {
    rdt.interrupt()
    rdt.join()

  }

  def writeTo(buf: RawBuffer, peer: SocketAddress): Unit = sch map {
    channel => {
      channel.send(buf, peer)
    }
  }
  def writeTo(buf: RawBuffer, eid: Long): Unit = {
    throw new RuntimeException("Method Not Implemented Yet")
  }


  def close(remoteAddress: SocketAddress): Unit = { }
}