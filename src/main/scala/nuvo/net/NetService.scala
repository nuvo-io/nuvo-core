package nuvo.net

import nuvo.nio.RawBuffer
import nuvo.concurrent.Service
import nuvo.nio.prelude._

object NetService {
  def apply(l: Locator, e: (RawBuffer, NetService) => Unit): Option[NetService] = {
    l.transport match {
      case "udp" => Some(new UDPNetService(l, e))
      case  "tcp" => Some(new TCPServiceAcceptor(l, e))
    }
  }

  def apply(l: String, e: (RawBuffer, NetService) => Unit): Option[NetService] = {
    Locator(l) match {
      case Some(locator) => NetService(locator, e)
      case None => None
    }
  }
}
abstract class NetService(val locator: Locator) extends Service {
  def start()
  def stop()
}
