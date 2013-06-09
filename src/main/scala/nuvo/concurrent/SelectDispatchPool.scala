package nuvo.concurrent

import java.nio.channels.{ClosedChannelException, ClosedByInterruptException}


object SelectDispatchPool {
  import nuvo.runtime.Config._
  def apply[I, O](allocator: () => I,
                  selector: (I) => O,
                  executor: (O) => Unit) =
    new SelectDispatchPool[I, O] (
      Concurrency.threadPoolSize,
      allocator,
      selector,
      executor
    )

  def apply[I, O](executorNum: Int,
                  allocator: () => I,
                  selector: (I) => O,
                  executor: (O) => Unit) =
    new SelectDispatchPool[I, O](
      executorNum,
      allocator,
      selector,
      executor
    )
}

/**
 * This executor pool implements the Leader-Follower Pattern for very efficiently dispatching
 * events over a pool of threads while minimizing the number of context switches along with
 * minimizing the memory allocations.
 *
 * This pool of executors uses and even number of threads that represent the couple

 * @param executorNum the number of executors to be used.
 * @param allocator the storage allocator
 * @param selector the selection logic, could be a read from a socket, etc.
 * @param executor the execution logic
 * @tparam I the input, usually whatever was allocated by the allocator
 * @tparam O the output of the selector and input for the executor
 */
class SelectDispatchPool[I, O](val executorNum: Int,
                               val allocator: () => I,
                               private val selector: (I) => O,
                               private val executor: (O) => Unit) {

  private class LFThread (val storage: I) extends Thread {
    private var interrupted = false

    override def run() {
      while (!interrupted) {
        try {
          // Select the task (such as read from net, etc.)
          val s = selector(this.storage)
          // Elect new Leader to continue selection
          executor(s)
          // Go back in the pool and relax
        } catch {
          case e: InterruptedException => interrupted = true
          case cie: ClosedByInterruptException => interrupted = true
          case cce: ClosedChannelException => interrupted = true
        }
      }
    }
  }

  private val threadPool =  (1 to executorNum) map { i => new LFThread(allocator()) }

  private var isRunning = false;


  /**
   * Starts to wait for the selector to unblock and then handles the specific
   * selection.
   */
  def start() {
    synchronized {
      if (isRunning == true)
        throw new IllegalThreadStateException
      else {
        isRunning = true
        threadPool foreach {_.start()}
      }
    }
  }
  /**
   * Stops execution.
   */
  def stop() {
    threadPool foreach {_.interrupt()}
    threadPool foreach {_.join()}
  }
}
