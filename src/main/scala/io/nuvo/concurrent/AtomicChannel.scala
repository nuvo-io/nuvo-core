package io.nuvo.concurrent

import scala.collection.immutable.Queue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import synchronizers._

class AtomicChannel[E] extends Channel[E] {
  private val res = new AtomicReference(Queue[E]())
  private val empty = new AtomicBoolean(true)


  override def put(e: E): Unit = {
    compareAndSet(res) { es =>
      es.enqueue(e)
    }
    empty.set(false)
  }

  override def take(): E = {
    while (empty.get()) { }
    var r: E = ???
    compareAndSet(res) { es =>
      val (e, xs) =  es.dequeue
      r = e
      xs
    }
    r
  }

  override def takeAll(): Queue[E] = compareAndSet(res) { xs => Queue[E]() }

  override def poll(): Option[E] = {
    if (empty.get()) None
    else {
      var r: Option[E] = None
      try {
        compareAndSet(res) { es =>
          val (e, xs) = es.dequeue
          r = Some(e)
          xs
        }
      }
      catch {
        case ne: NoSuchElementException => r = None
      }
      r
    }
  }

  override def iterator: Iterator[E] = res.get().iterator

}
