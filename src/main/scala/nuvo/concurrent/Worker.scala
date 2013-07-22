package nuvo.concurrent

/**
 * This class is intented to perform a long running task on another thread
 */

import Implicits._
import nuvo.runtime.Config._

object Worker {
  def run[T](task: => T) = new Worker(task)

  def runLoop[T](task: => T) = new Worker( {
    var interrupted = false
    while (!interrupted) {
      try {
        task
      } catch {
        case ie: InterruptedException => interrupted = true
      }
    }
  })
}

class Worker[T](task: => T) {
  private val executor = new Thread(task)
  executor.start()

  def interrupt() {
    if (executor.isAlive) {
      executor.interrupt()
      executor.join()
    }
  }
}
