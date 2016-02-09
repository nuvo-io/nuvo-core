package io.nuvo.net

import java.net.{InetAddress, InetSocketAddress}
import java.nio.channels.{ClosedChannelException, ServerSocketChannel}
import io.nuvo.nio.prelude._
import io.nuvo.nio.RawBuffer
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import io.nuvo.concurrent.synchronizers._
import java.util.concurrent.locks.ReentrantLock


class TCPServiceAcceptor(endpoint: Endpoint, val executor: (RawBuffer, NetService) => Any) extends NetService(endpoint) {

  require {
    endpoint.proto == Protocol.tcp
  }
  val lock = new ReentrantLock()

  var services: List[TCPNetService] = List[TCPNetService]()

  @volatile private var running = false

  val (channel, cleanup) = createChannel(endpoint)

  private def createChannel(endpoint: Endpoint) = {
    val channel = ServerSocketChannel.open()
    channel.bind(endpoint.socketAddress)
    channel.configureBlocking(true)
    // TODO: Set socket options

    (channel,
      () => {
        channel.close()
      }
      )
  }

  def accept() {
    val f = Future {
      val c = channel.accept()
      c.configureBlocking(true)
      val svc = new TCPNetService(c, endpoint, executor)
      svc.start()
      syncrhonized(lock) {
        services = svc :: services
      }
    }

    f onSuccess  {
      case _ => if (running) accept()
    }

    f onFailure {
      case _ => {
        running = false
        this.stop()
      }
    }
  }

  def start() {
    synchronized {
      if (!running) {
        running = true
        accept()
      }
    }
  }

  def stop() {
    synchronized {
      running = false
      channel.close()
      services foreach(_.stop())
    }
  }
}
