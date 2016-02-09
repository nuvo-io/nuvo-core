package io.nuvo.net

import java.nio.channels.{ServerSocketChannel, Selector, DatagramChannel, SelectionKey}
import java.net.{SocketAddress, InetSocketAddress, StandardSocketOptions, NetworkInterface}
import java.util.concurrent.atomic.AtomicReference

import io.nuvo.nio.RawBuffer
import io.nuvo.runtime.Config._
import io.nuvo.concurrent.synchronizers._
import java.io.IOException
import io.nuvo.nio.prelude._


/**
 * This class uses a thread to read messages from connected channels and one to write into connected channels.
 *
 * @param endpoints the endpoints at which this message pump will be receiving data
 * @param bufSize the size of the buffer used by this message pump.
 * @param reader does pre-processing on the receiving data, this is usually done for framing.
 * @param consumer  consumes a message from the pump and gives back the buffer to be used for the next I/O operation.
 */
class UDPInMessagePump(val endpoints: List[Endpoint], reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int, consumer: MessagePumpMsg => RawBuffer) extends InMessagePump {

  require {
    endpoints.map (_.proto == Protocol.udp).foldRight(true)(_ && _)
  }

  val selector = Selector.open()

  private val endpointMapRef = new AtomicReference(Map[SocketAddress, (DatagramChannel, () => Unit)]())

  def this(endpoint: Endpoint, reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int, consumer: MessagePumpMsg => RawBuffer) =
    this(List(endpoint), reader, bufSize, consumer)



  private val rdispatcher = new Runnable() {
    def run() {
      var buf = RawBuffer.allocateDirect(bufSize)

      compareAndSet(endpointMapRef) {
        endpointMap => {
          endpoints.toStream.map(endpoint => {
            val sockAddr =
              new InetSocketAddress(endpoint.address, endpoint.port)

            if (endpoint.address.isMulticastAddress) {
              val ni = NetworkInterface.getByName(Networking.defaultNIC);
              val channel = DatagramChannel.open(endpoint.protoFamily)
              channel.setOption(StandardSocketOptions.SO_REUSEADDR, Boolean.box(true))
              channel bind new InetSocketAddress(endpoint.port)
              channel setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);
              channel.configureBlocking(false)
              val key = channel.join(endpoint.address, ni)
              channel.register(selector, SelectionKey.OP_READ, sockAddr)
              (sockAddr, (channel, () => {
                key.drop()
                channel.close()
              }))
            }
            else {

              val channel = DatagramChannel.open()
              channel.setOption(StandardSocketOptions.SO_REUSEADDR, Boolean.box(true))
              channel.bind(sockAddr)
              channel.configureBlocking(false)
              log.log("Channel bound at: " + channel.socket().getLocalAddress);
              channel.register(selector, SelectionKey.OP_READ, sockAddr)
              // TODO: Set socket options
              (sockAddr, (channel,
                () => {
                  channel.close()
                }))
            }
          }).toMap
        }
      }


      var interrupted = false

      while (!interrupted) {
        var k: SelectionKey = null
        try {
          val keys = selector.selectedKeys()
          val iterator = keys.iterator()

          while (iterator.hasNext) {
            k = iterator.next()
            iterator.remove()

            if (k.isReadable) {
              val remoteAddress = k.attachment().asInstanceOf[SocketAddress]
              val d = reader(k, buf)
              buf = consumer(DataAvailable(d, remoteAddress, UDPInMessagePump.this))
            }

          }
        } catch {
          case ie: InterruptedException => {
            interrupted = true
//            compareAndSet() { acceptorMap =>
//              acceptorMap.foreach(_._2.close())
//              Map[SocketAddress, ServerSocketChannel]()
//            }
            consumer(ServiceStopped)
          }
          case ioe: IOException => {
            if (k != null) {
              val remoteAddress = k.attachment().asInstanceOf[SocketAddress]
              k.cancel()
              k.channel().close()
//              compareAndSet(connectionMapRef) { connectionMap => connectionMap - remoteAddress}
//              consumer(ConnectionClosed(remoteAddress))
//              log.warning(s"Closing connection to: $remoteAddress ")
//              log.warning(s"Due to error:\n\t$ioe")
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


  override def start(): Unit = rdt.start()

  override def stop(): Unit = {
    rdt.interrupt()
    rdt.join()
  }

  override def close(socket: Endpoint): Unit = ???

  override def add(endPoint: Endpoint): Unit = ???

  override def close(addr: SocketAddress): Unit = ???
}

/*




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


  def close(remoteAddress: SocketAddress): Unit = {
    // Nothing to close
  }

  override def add(endpoint: Endpoint): Unit = ???
 */
