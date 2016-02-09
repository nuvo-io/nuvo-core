package io.nuvo.net

import java.nio.channels.NetworkChannel
import io.nuvo.nio.RawBuffer
import scala.util.{Try, Success, Failure}

object NetLink {
  def apply(e: Endpoint): Try[NetLink] = {
    e.proto match {
      case Protocol.udp =>  try { Success(new UDPNetLink(e)) } catch { case t: Throwable => Failure(t) }
      case Protocol.tcp => try { Success(new TCPNetLink(e)) } catch { case t: Throwable => Failure(t) }
      case _ => Failure(new RuntimeException("Unknown/Unsupported tranport"))
    }
  }

  def apply(l: String): Try[NetLink] = {
    Endpoint(l) match {
      case Success(endpoint) => NetLink(endpoint)
      case Failure(t) => Failure(t)
    }
  }
}

/**
 * Represents a link over which data can be sent.
 */

trait NetLink {
  def endpoint: Endpoint
  def channel: NetworkChannel
  def write(data: RawBuffer): Int
  def read(data: RawBuffer): Int
}
