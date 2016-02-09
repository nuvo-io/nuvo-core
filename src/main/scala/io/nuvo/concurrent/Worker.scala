package io.nuvo.concurrent

/**
 * This class is intented to perform a long running task on another thread
 */

import Implicits._
import io.nuvo.runtime.Config._

object Worker {
  def run(task: () => Any) = new Worker(task)

  def runLoop(task: () => Any): Worker = new Worker( () => {
    var interrupted = false
    while (!interrupted) {
      try {
        task()
      } catch {
        case ie: InterruptedException => interrupted = true
      }
    }
  })
}

class Worker(task: () => Any) {
  private val executor = new Thread(task)
  executor.start()

  def interrupt() {
    if (executor.isAlive) {
      executor.interrupt()
      executor.join()
    }
  }
}
