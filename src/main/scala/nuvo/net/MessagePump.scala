package nuvo.net

import java.nio.channels.{ServerSocketChannel, Selector, SocketChannel, SelectionKey}
import nuvo.nio.RawBuffer
import java.net.{InetAddress, InetSocketAddress}
import nuvo.runtime.Config._
import nuvo.nio.prelude._
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong


abstract class MessagePump {
  def writeTo(buf: RawBuffer, connectionID: Long)
  def start()
  def stop()
  def close(connectionId: Long)
}

abstract class MessagePumpMessage
case class DataAvailable(buf: RawBuffer, cid: Long, mp: MessagePump) extends MessagePumpMessage
case class ConnectionClosed(cid: Long) extends MessagePumpMessage
case object ServiceStopped extends MessagePumpMessage


