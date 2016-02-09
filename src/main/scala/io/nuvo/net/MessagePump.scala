package io.nuvo.net

import io.nuvo.nio.RawBuffer
import java.net.{InetSocketAddress, SocketAddress}
import scala.util.{Failure, Try}
import java.nio.channels.SelectionKey


object InMessagePump {

  def apply(endpoint: Endpoint,
    reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int,
    consumer: MessagePumpMsg => RawBuffer) = {
    endpoint.proto match {
      case Protocol.tcp => Try(new TCPInMessagePump(endpoint, reader, bufSize, consumer))
      case Protocol.udp => Try(new UDPInMessagePump(endpoint, reader, bufSize,   consumer))
    }
  }
}

object OutMessagePump {

  def apply(endpoint: Endpoint,
            reader: (SelectionKey, RawBuffer) => RawBuffer, bufSize: Int,
            consumer: MessagePumpMsg => RawBuffer) = {
    endpoint.proto match {
      case Protocol.tcp => Try(new TCPOutMessagePump(endpoint))
      case Protocol.udp => Try(new UDPOutMessagePump(endpoint))
    }
  }
}


trait InMessagePump {
  def start(): Unit
  def stop(): Unit
  def close(socket: Endpoint): Unit
  def close(addr: SocketAddress): Unit
  def add(endPoint: Endpoint): Unit
}

trait OutMessagePump {
  def start(): Unit
  def stop(): Unit
  def close(endpoint: Endpoint): Unit
  def close(addr: SocketAddress): Unit
  def add(endpoint: Endpoint): Unit

  /**
   * Writes the content of the buffer to the specified address.
   *
   * @param buf the buffer to be written.
   * @param address the destination address.
   */
  def writeTo(buf: RawBuffer, address: SocketAddress): Unit

  /**
   * Write the content of a buffer to all the endpoint currently connected.
   *
   * @param buf the buffer to be written.
   */
  def writeTo(buf: RawBuffer)
}

trait MessagePump extends InMessagePump with OutMessagePump

abstract class MessagePumpMsg
case class DataAvailable(buf: RawBuffer, remoteAddress: SocketAddress, mp: InMessagePump) extends MessagePumpMsg
case class ConnectionClosed(remoteAddress: SocketAddress) extends MessagePumpMsg
case object ServiceStopped extends MessagePumpMsg
