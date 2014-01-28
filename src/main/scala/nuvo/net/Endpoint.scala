package nuvo.net

import scala.util.{Failure, Try, Success}
import java.net.{InetSocketAddress, StandardProtocolFamily, InetAddress}

// protocol/ip:port
// udp/192.168.1.1:9900
// tcp/192.168.1.1:9900

object Endpoint {
  val proto = List(Protocol.tcp, Protocol.udp)

  def apply(e: String): Try[Endpoint] = try {
    val p = e split "/"

    if (proto.contains(p(0).toLowerCase) == false) throw new RuntimeException("Invalid or unsupported protocol: " + p(0))

    val endpoint = if (p.length == 2) {
      val pa = p(1) split ":"
      if (pa.length == 2 ) Success(new Endpoint(p(0), InetAddress.getByName(pa(0)), pa(1).toInt))
      else throw new RuntimeException(s"$e has an invalid structure\n it should be in the form transport/addr:port, e.g. udp/10.1.2.3:4567")
    }
    else throw new RuntimeException(s"$e has an invalid structure\n it should be in the form transport/addr:port, e.g. udp/10.1.2.3:4567")
    endpoint
  }  catch {
    case t: Throwable => Failure(t)
  }
}

class Endpoint(val proto: String,  val address: InetAddress, val port: Int) {
  override def toString =
    proto + address + ":"+ port

  val protoFamily =
    if (address.toString.length > 15) StandardProtocolFamily.INET6
    else StandardProtocolFamily.INET

  val socketAddress = new InetSocketAddress(address, port)
}
