package io.nuvo.concurrent

import scala.language.higherKinds
import java.util.concurrent.locks.{ReentrantReadWriteLock, ReentrantLock}
import java.util.concurrent.atomic.AtomicReference

package object synchronizers {

  /**
   * Atomically compares and sets the value of an atomic a reference to f(a).
   *
   * @param ref An atomic reference
   * @param fun a function transforming the content of the amtomic reference
   * @tparam T the type wrapped in the atomic reference
   * @return the old value of the atomic reference
   */
  final def compareAndSet[T](ref: AtomicReference[T])(fun: (T) => T): T = {
    var cas = false
    var or: T = ref.get()
    do {
      or = ref.get()
      val res = fun(or)
      cas = ref.compareAndSet(or, res)
    } while (!cas)
    or
  }

  def syncrhonized[T](lock: ReentrantLock)(lambda: => T) = {
    lock.lock()
    try lambda finally lock.unlock()
  }

  def synchronizedRead[T](lock: ReentrantReadWriteLock)(lambda: => T) = {
    lock.readLock().lock()
    try lambda finally lock.readLock().unlock()
  }

  def synchronizedWrite[T](lock: ReentrantReadWriteLock)(lambda: => T) = {
    lock.writeLock().lock()
    try lambda finally lock.writeLock().unlock()
  }
}
