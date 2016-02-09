package io.nuvo.net

import io.nuvo.nio.RawBuffer
import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.SocketChannel
import io.nuvo.nio.prelude._
import io.nuvo.runtime.Config._
import io.nuvo.concurrent.synchronizers._
import java.util.concurrent.atomic.AtomicReference


class TCPOutMessagePump(val endpoints: List[Endpoint]) extends OutMessagePump {

  require {
    endpoints.map(_.proto == Protocol.tcp).foldRight(true)(_ && _)
  }

  private val connectionMapRef = new AtomicReference(Map[SocketAddress, SocketChannel]())

  def this(endpoint: Endpoint) = this(List(endpoint))


  override def start(): Unit = compareAndSet(connectionMapRef) { connectionMap =>
    endpoints.toStream.map { endpoint =>
      val addr = new InetSocketAddress(endpoint.address, endpoint.port)
      val ch = SocketChannel.open(addr)
      (addr, ch)
    }.toMap
  }

  override def stop(): Unit = compareAndSet(connectionMapRef) { connectionMap =>
    connectionMap.foreach { e =>
      e._2.close()
    }
    Map[SocketAddress, SocketChannel]()
  }

  /**
   * Writes the content of the buffer to the specified address.
   *
   * @param buf the buffer to be written.
   * @param address the destination address.
   */
  override def writeTo(buf: RawBuffer, address: SocketAddress): Unit = {
    connectionMapRef.get().get(address).map( _.write(buf) ).orElse {
      log.warning(s"Unable to send data. The Message Pumg is not connected to $address")
      None
    }
  }

  /**
   * Write the content of a buffer to all the endpoint currently connected.
   *
   * @param buf the buffer to be written.
   */
  override def writeTo(buf: RawBuffer): Unit = compareAndSet(connectionMapRef) {
    connectionMap => {
      connectionMap.foreach(_._2.write(buf))
      connectionMap
    }
  }



  override def close(addr: SocketAddress): Unit = compareAndSet(connectionMapRef) {
    connectionMap => {
      val e = connectionMap.get(addr)
      e.map(_.close)
      connectionMap - addr
    }
  }

  override def close(endpoint: Endpoint): Unit = {
    val addr = new InetSocketAddress(endpoint.address, endpoint.port)
    close(addr)
  }


  override def add(endpoint: Endpoint): Unit = compareAndSet(connectionMapRef) {
    connectionMap => {
      val addr = new InetSocketAddress(endpoint.address, endpoint.port)
      val ch = SocketChannel.open(addr)
      connectionMap + (addr -> ch)
    }

  }


}

