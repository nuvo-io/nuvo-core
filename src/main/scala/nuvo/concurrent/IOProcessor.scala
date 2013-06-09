package nuvo.concurrent

import nuvo.net.MessagePayload
import nuvo.nio.RawBuffer
import nuvo.net.MessagePayload
import java.util.concurrent.{LinkedBlockingQueue, LinkedBlockingDeque, Semaphore}
import nuvo.nio.prelude._
import nuvo.runtime.Config._

abstract class IOProcessor {
  def process(message: MessagePayload)
  def start()
  def stop()
}

class LFIOProcessor(executorNum: Int, bufSize: Int, msgBufLen: Int, processor: MessagePayload => Any, handOverLevel: Int = 2) extends IOProcessor {
  private val payloads = new LinkedBlockingQueue[MessagePayload](msgBufLen)
  private val buffers = new LinkedBlockingDeque[RawBuffer](msgBufLen)

  (1 to msgBufLen) map { x => buffers.putLast(RawBuffer.allocate(bufSize)) }

  def process(message: MessagePayload) {
    val buf = buffers.takeLast()
    buf.put(message.buf)
    buf.flip()
    payloads.put(MessagePayload(buf, message.cid, message.mp))
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
            } catch {
              case t: Throwable => {
                log.error(s"Error while processing message: " + t.printStackTrace())
                log.log("Closing connection: " + msg.cid)
                msg.mp.close(msg.cid)
              }
            }

            msg.buf.clear()
            buffers.putLast(msg.buf)
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