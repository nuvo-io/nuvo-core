package nuvo.net

import java.nio.channels.NetworkChannel
import nuvo.nio.RawBuffer

object NetLink {
  def apply(l: Locator): NetLink = {
    l.transport match {
      case "udp" =>  new UDPNetLink(l)
      case "tcp" => new TCPNetLink(l)
      case _ => throw new RuntimeException("Unknown/Unsupported tranport")
    }
  }

  def apply(l: String): NetLink = {
    Locator(l) match {
      case Some(locator) => NetLink(locator)
      case None => throw new RuntimeException("Unknown/Unsupported locator: "+ l)
    }
  }
}

/**
 * Represents a link over which data can be sent.
 */

trait NetLink {
  def locator: Locator
  def channel: NetworkChannel
  def write(data: RawBuffer): Int
  def read(data: RawBuffer): Int
}
