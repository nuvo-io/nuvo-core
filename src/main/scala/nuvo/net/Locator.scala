package nuvo.net

// proto:encoding/transport:ip:port
// nuvo:protostuff/udp:192.168.1.1:9900
// nuvo:json/udp:192.168.1.1:9900
// nuvo:json/udp:239.255.1.1:9000

object Locator {
  def apply(l: String): Option[Locator] = {
    val p = l split ("/")
    if (p.length == 2) {
      val pe = p(0) split (":")
      val pap = p(1) split(":")
      if (pe.length == 2 && pap.length == 3)
        Some(new Locator(pe(0), pe(1), pap(0), pap(1), pap(2).toInt))
      else None
    }
    else None

  }
}
class Locator(val proto: String, val encoding: String, val transport: String, val address: String, val port: Int) {
  override def toString =
    proto + ":" + encoding +"/"+ transport + ":" + address + ":"+ port
}
