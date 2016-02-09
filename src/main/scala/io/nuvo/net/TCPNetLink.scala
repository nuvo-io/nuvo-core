package io.nuvo.net

import java.net.{InetSocketAddress, InetAddress}
import java.nio.channels.SocketChannel
import io.nuvo.nio.prelude._
import io.nuvo.nio.RawBuffer
import io.nuvo.runtime.Config.Networking

class TCPNetLink(val endpoint: Endpoint) extends NetLink {

  private def initChannel() = {
    val ch = SocketChannel.open(endpoint.socketAddress)
    ch.socket().setTcpNoDelay(Networking.Socket.TCP_NO_DELAY)
    ch.socket().setPerformancePreferences(Networking.Socket.Performance._1, Networking.Socket.Performance._2, Networking.Socket.Performance._3)
    ch
  }

  override val channel: SocketChannel =  initChannel()

  def write(data: RawBuffer) =  channel.write(data)
  def read(data: RawBuffer) = channel.read(data)
}

