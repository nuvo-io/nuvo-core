package io.nuvo.net

import io.nuvo.nio.RawBuffer
import io.nuvo.concurrent.Service
import io.nuvo.nio.prelude._
import scala.util.Success
import scala.util.{Try, Success, Failure}



object NetService {


  def apply(endpoint: Endpoint, e: (RawBuffer, NetService) => Any): Try[NetService] = endpoint.proto match {
    case Protocol.udp => Try(new UDPNetService(endpoint, e))
    case Protocol.tcp => Try(new TCPServiceAcceptor(endpoint, e))
  }

  def apply(endpoint: String, e: (RawBuffer, NetService) => Any): Try[NetService] =
    Endpoint(endpoint) flatMap {ep =>  NetService(ep, e) }


}

abstract class NetService(val endpoint: Endpoint) extends Service {
  def start()
  def stop()
}
