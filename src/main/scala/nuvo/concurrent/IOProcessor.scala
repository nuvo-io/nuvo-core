package nuvo.concurrent

import nuvo.net.{MessagePumpMessage, DataAvailable}
import nuvo.nio.RawBuffer
import java.util.concurrent.{LinkedBlockingQueue, LinkedBlockingDeque, Semaphore}
import nuvo.nio.prelude._
import nuvo.runtime.Config._

abstract class IOProcessor {
  def process(message: MessagePumpMessage): RawBuffer
  def start()
  def stop()
}

class LFIOProcessor(executorNum: Int, bufSize: Int, msgBufLen: Int, processor: MessagePumpMessage => Any, handOverLevel: Int = 2) extends IOProcessor {
  private val payloads = new LinkedBlockingQueue[MessagePumpMessage](msgBufLen)
  private val buffers = new LinkedBlockingDeque[RawBuffer](msgBufLen)

  (1 to msgBufLen) map { x => buffers.putLast(RawBuffer.allocateDirect(bufSize)) }

  def process(message: MessagePumpMessage): RawBuffer = {
    message match {
      case DataAvailable(mbuf, cid, mp) => {
        val buf = buffers.takeLast()
        // buf.put(mbuf)
        // buf.flip()
        payloads.put(DataAvailable(mbuf, cid, mp))
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
                  case DataAvailable(_, cid, mp) => {
                    log.log("Closing connection: " + cid)
                    mp.close(cid)
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