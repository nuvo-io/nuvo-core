package io.nuvo.concurrent

import scala.Predef._
import java.util.concurrent.{LinkedBlockingDeque, Semaphore}
import io.nuvo.net.event._
import java.nio.channels.{CancelledKeyException, SelectionKey, SelectableChannel, Selector}
import io.nuvo.nio.RawBuffer
import io.nuvo.nio.prelude._
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock

object Reactor {
  def apply(executorNum: Int, bufSize: Int, handOverLevel: Int) = new Reactor(executorNum, bufSize, handOverLevel)
}

/**
 * A pool of executors using the leader-follower pattern for waiting on a
 * selector and dispatching work
 *
 */
class Reactor(executorNum: Int, bufSize: Int, val handOverLevel: Int = 2) {
  require {
    executorNum >= 2
  }

  final val selector = Selector.open()

  @inline private final def dispatch(sk: SelectionKey, buf: RawBuffer) {
    val disp = sk.attachment().asInstanceOf[PartialFunction[ReactorEvent, Unit]]
    if (sk.isReadable) disp(OnRead(sk, buf))
    if (sk.isWritable) disp(OnWrite(sk, buf))
    if (sk.isConnectable) disp(OnConnect(sk, buf))
    if (sk.isAcceptable) disp(OnAccept(sk, buf))
  }

  /**
   * Registers a selectable channel with this executor pool which
   * from now on will take care of dispatching the relevant events.
   *
   * @param sc a [[java.nio.channels.SelectableChannel]]
   * @param select the set of events we want to select upon
   * @return the [[java.nio.channels.SelectionKey]] representing this selection
   */
  def registerChannel(sc: SelectableChannel,
                      select: Int)
                     (dispatcher: PartialFunction[ReactorEvent, Unit]) = {
    selector.wakeup()
    sc.register(selector, select, dispatcher)
  }

  private final class LFExecutor extends Thread {
    private val leaderSemaphone = new Semaphore(0)
    private var interrupted = false
    private val buf = RawBuffer.allocate(bufSize)
    var count = 0

    def leader() {
      leaderSemaphone.release()
    }

    override def run() {
      while (!interrupted) {
        try {
          leaderSemaphone.acquire()
          var leader = true;
          while (leader) {
            try {
              do {
                selector.select()
              } while (selector.selectedKeys().isEmpty)


              val skeys: java.util.Set[SelectionKey] = selector.selectedKeys()
              val size = skeys.size()
              val k = skeys.iterator().next()
              skeys.remove(k)
              val ops = k.interestOps()
              k.interestOps(0)

              // Hand over to another leader if there is sufficient work to do
              // and if there is a leader to take over.
              // Otherwise to limit context switched keep the leadership
              if (size > handOverLevel) {
                leader = !electLeader()
              }

              dispatch(k, buf)
              k.interestOps(ops)
            } catch {
              case t: Throwable =>
            }
            finally {
              if (leader == false)
                threadPool.putLast(this)
            }
          }


        }
        catch {
          case e: InterruptedException => interrupted = true
          case cke: CancelledKeyException =>
        }
      }
    }
  }

  private var threadPool = new LinkedBlockingDeque[LFExecutor](executorNum)
  for (i <- 1 to executorNum) {
    val t = new LFExecutor
    threadPool.putLast(t)
    t.start()
  }

  private var isRunning = false;

  private def electLeader(): Boolean = {
    // If there are no more thread we just wait for one to become available.
    val t = threadPool.pollLast()
    if (t != null) {
      t.leader()
      true
    }
    else false
  }

  /**
   * Starts to wait for the selector to unblock and then handles the specific
   * select.
   */
  def start() {
    synchronized {
      if (isRunning == true)
        throw new IllegalThreadStateException
      else {
        isRunning = true
        electLeader()
      }
    }
  }
  /**
   * Stops execut.
   */
  def stop() {
    import scala.collection.JavaConversions._
    // Cancel all selects
    selector.keys().foreach(_.cancel())
    threadPool foreach (_.interrupt())
    threadPool foreach (_.join())
  }
}


class CReactor(executorNum: Int, bufSize: Int) {
  require {
    executorNum >= 2
  }

  final val selector = Selector.open()
  final val leaderLock = new ReentrantLock()

  @inline private final def dispatch(sk: SelectionKey, buf: RawBuffer) {
    val disp = sk.attachment().asInstanceOf[PartialFunction[ReactorEvent, Unit]]
    if (sk.isReadable) disp(OnRead(sk, buf))
    if (sk.isWritable) disp(OnWrite(sk, buf))
    if (sk.isConnectable) disp(OnConnect(sk, buf))
    if (sk.isAcceptable) disp(OnAccept(sk, buf))
  }

  /**
   * Registers a selectable channel with this executor pool which
   * from now on will take care of dispatching the relevant events.
   *
   * @param sc a [[java.nio.channels.SelectableChannel]]
   * @param select the set of events we want to select upon
   * @return the [[java.nio.channels.SelectionKey]] representing this selection
   */
  def registerChannel(sc: SelectableChannel,
                      select: Int)
                     (dispatcher: PartialFunction[ReactorEvent, Unit]) = {
    selector.wakeup()
    sc.register(selector, select, dispatcher)
  }

  private final class LFExecutor extends Thread {
    private var interrupted = false
    private val buf = RawBuffer.allocate(bufSize)

    override def run() {
      var noKeys = true
      var skeys: java.util.Set[SelectionKey] = null
      var leader = false
      while (!interrupted) {
        try {
          if (!leader) {
            leaderLock.lock()
            leader = true
          }
            do {
              selector.select()
              skeys = selector.selectedKeys()
            } while (skeys.isEmpty)


            val size = skeys.size()
            val k = skeys.iterator().next()
            skeys.remove(k)

            if (size > 1) {
              leaderLock.unlock()
              leader = false
            }
            val ops = k.interestOps()
            k.interestOps(0)

            // Hand over to another leader if there is sufficient work to do
            // and if there is a leader to take over.
            // Otherwise to limit context switched keep the leadership

            dispatch(k, buf)
            k.interestOps(ops)
        }
        catch {
          case e: InterruptedException => interrupted = true
          case cke: CancelledKeyException =>
        }
      }
    }
  }

  private var threadPool = (1 to executorNum) map (x => {new LFExecutor})


  private var isRunning = false;


  /**
   * Starts to wait for the selector to unblock and then handles the specific
   * select.
   */
  def start() {
    synchronized {
      if (isRunning == true)
        throw new IllegalThreadStateException
      else {
        isRunning = true
        threadPool foreach(_.start())
      }
    }
  }
  /**
   * Stops execut.
   */
  def stop() {
    import scala.collection.JavaConversions._
    // Cancel all selects
    selector.keys().foreach(_.cancel())
    threadPool foreach (_.interrupt())
    threadPool foreach (_.join())
  }
}

