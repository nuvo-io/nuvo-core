package io.nuvo.concurrent

import collection.Iterator
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue}

object ArrayBlockingChannel {
  def apply[E](capacity: Int) = new BlockingChannel[E] {
    val channel: BlockingQueue[E] = new java.util.concurrent.ArrayBlockingQueue[E](capacity)
  }
}

object ListBlockingChannel {
  def apply[E](capacity: Int) = new BlockingChannel[E] {
    val channel: BlockingQueue[E] = new java.util.concurrent.LinkedBlockingDeque[E](capacity)
  }
}

/**
 * The <code>BlockingChannel</code> is a channel which
 *
 */

abstract class BlockingChannel[E] extends Channel[E] {

  val channel: BlockingQueue[E]

  def put(e: E) = channel.put(e)

  def take() = channel.take()

  def takeAll() =  throw new RuntimeException("Not Supported")


  def poll() = {
    val e = channel.poll()
    if (e == null) None else Some(e)
  }

  import scala.collection.JavaConversions._
  def iterator: Iterator[E] = channel.iterator
}
