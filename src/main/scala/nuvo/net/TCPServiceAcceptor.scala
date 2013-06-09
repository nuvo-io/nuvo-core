package nuvo.net

import java.net.{InetAddress, InetSocketAddress}
import java.nio.channels.{ClosedChannelException, ServerSocketChannel}
import nuvo.nio.prelude._
import nuvo.nio.RawBuffer
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import nuvo.concurrent.synchronizers._
import java.util.concurrent.locks.ReentrantLock


class TCPServiceAcceptor(l: Locator, val executor: (RawBuffer, NetService) => Unit) extends NetService(l) {

  require {
    locator.transport == "tcp"
  }
  val lock = new ReentrantLock()

  var services: List[TCPNetService] = List[TCPNetService]()

  @volatile private var running = false

  val (channel, cleanup) = createChannel(locator)

  private def createChannel(locator: Locator) = {
    val addr =
      new InetSocketAddress(InetAddress.getByName(locator.address), locator.port)

    val channel = ServerSocketChannel.open()
    channel.bind(addr)
    channel.configureBlocking(true)
    // TODO: Set socket options

    (channel,
      () => {
        channel.close()
      }
      )
  }

  def accept() {
    val f = future {
      val c = channel.accept()
      c.configureBlocking(true)
      val svc = new TCPNetService(c, locator, executor)
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
