package io.nuvo.concurrent

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference


object MVar {
  def newEmptyMVar[T] = new MVar[T](None)
  def apply[T](v: T) = new MVar[T](Some(v))
}

/**
 * MVar implementation inspired to Haskell's MVar type
 */
class MVar[T](v: Option[T]) {
  private val ref = new AtomicReference[Option[T]](v)
  private val canRead = if (v == None) new Semaphore(0, true) else new Semaphore(1, true)
  private val canWrite = if (v == None) new Semaphore(1, true) else new Semaphore(0, true)

  /**
   * Read the MVar and returns None if it is empty.
   * @return Some value or Node depending on the content of the MVar
   */
  def read() : Option[T]  = ref.get()

  def isEmpty: Boolean = ref.get() == None

  def modify(v: T): T = {
    val ov = take()
    put(v)
    ov
  }

  def modify(f: (T) => T): T = {
    val ov = take()
    put(f(ov))
    ov
  }

  def take(): T = {
    canRead.acquire()
    val r = ref.get().get
    canWrite.release()
    r
  }

  def put(v: T): Unit = {
    canWrite.acquire()
    ref.set(Some(v))
    canRead.release()
  }
}
