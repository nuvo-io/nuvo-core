package io.nuvo.concurrent

import io.nuvo.net.{MessagePumpMsg, DataAvailable}
import io.nuvo.nio.RawBuffer
import java.util.concurrent.{LinkedBlockingQueue, LinkedBlockingDeque, Semaphore}
import io.nuvo.nio.prelude._
import io.nuvo.runtime.Config._

abstract class IOProcessor {
  def process(message: MessagePumpMsg): RawBuffer
  def start()
  def stop()
}

class LFIOProcessor(executorNum: Int, bufSize: Int, msgBufLen: Int, processor: MessagePumpMsg => Any, handOverLevel: Int = 2) extends IOProcessor {
  private val payloads = new LinkedBlockingQueue[MessagePumpMsg](msgBufLen)
  private val buffers = new LinkedBlockingDeque[RawBuffer](msgBufLen)

  (1 to msgBufLen) map { x => buffers.putLast(RawBuffer.allocateDirect(bufSize)) }

  def process(message: MessagePumpMsg): RawBuffer = {
    message match {
      case m @ DataAvailable(mbuf, cid, mp) => {
        val buf = buffers.takeLast()
        payloads.put(m)
        buf
      }
      case m @ _ => {
        payloads.put(m)
        null
      }
    }
  }


  private final class LFExecutor extends Thread {
    private val leaderSemaphone = new Semaphore(0)
    private var interrupted = false
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
            val msg = payloads.take()
            if (payloads.size() > handOverLevel) {
              leader = !electLeader()
            }
            try {
              processor(msg)
              msg match {
                case DataAvailable(buf, _, _) => {
                  buf.clear()
                  buffers.putLast(buf)
                }
                case _ =>
              }
            } catch {
              case t: Throwable => {
                log.error(s"Error while processing message: " + t.printStackTrace())
                msg match {
                  case DataAvailable(_, remoteAddress, mp) => {
                    log.log("Closing connection: " + remoteAddress)
                    mp.close(remoteAddress)
                  }
                }
              }
            }
            if (leader == false)
              threadPool.putLast(this)
          }
        }
        catch {
          case e: InterruptedException => interrupted = true
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
    threadPool foreach (_.interrupt())
    threadPool foreach (_.join())
  }
}
