package io.nuvo.net

import scala.util.{Failure, Success, Try}
// proto:encoding@endpoint -- where endpoint == tranport/address:port
// nuvo:protostuff@udp/192.168.1.1:9900
// nuvo:json@udp/192.168.1.1:9900
// nuvo:json@udp/239.255.1.1:9000

object Locator {

  def apply(l: String): Try[Locator] = {

    val p = l split ("@")
    val fail = Failure(new RuntimeException(s"Invalid Locator Format: $l"))
    if (p.length == 2) {
      val pe = p(0) split (":")
      if (pe.length == 2 )
        Endpoint(p(1)) map { e => new Locator(pe(0), pe(1), e)}
      else fail
    } else fail
  }
}

class Locator(val proto: String, val encoding: String, val endpoint: Endpoint) {
  override def toString =
    proto + ":" + encoding +"@"+ endpoint
}

