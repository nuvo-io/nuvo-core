package io.nuvo.net

import java.net.SocketAddress
import io.nuvo.nio.RawBuffer

/**
 * Created by io.nuvo on 26/05/14.
 */
class UDPOutMessagePump(val endpoints: List[Endpoint]) extends OutMessagePump {

  def this(endpoint: Endpoint) = this(List(endpoint))

  override def start(): Unit = ???

  override def stop(): Unit = ???

  /**
   * Writes the content of the buffer to the specified address.
   *
   * @param buf the buffer to be written.
   * @param address the destination address.
   */
  override def writeTo(buf: RawBuffer, address: SocketAddress): Unit = ???

  /**
   * Write the content of a buffer to all the endpoint currently connected.
   *
   * @param buf the buffer to be written.
   */
  override def writeTo(buf: RawBuffer): Unit = ???

  override def close(endpoint: Endpoint): Unit = ???

  override def close(addr: SocketAddress): Unit = ???

  override def add(endpoint: Endpoint): Unit = ???
}
