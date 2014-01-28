package nuvo.net

import nuvo.nio.RawBuffer
import java.net.SocketAddress


abstract class MessagePump {
  def writeTo(buf: RawBuffer, remoteAddress: SocketAddress): Unit
  def start(): Unit
  def stop(): Unit
  def close(remoteAddress: SocketAddress): Unit
}

abstract class MessagePumpMessage
case class TCPDataAvailable(buf: RawBuffer, remoteAddress: SocketAddress, mp: MessagePump) extends MessagePumpMessage
case class UDPDataAvailable(buf: RawBuffer, peer: SocketAddress, mp: MessagePump) extends MessagePumpMessage
case class TCPConnectionClosed(remoteAddress: SocketAddress) extends MessagePumpMessage
case object ServiceStopped extends MessagePumpMessage


