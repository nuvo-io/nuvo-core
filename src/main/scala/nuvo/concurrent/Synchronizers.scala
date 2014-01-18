package nuvo.concurrent

import scala.language.higherKinds
import java.util.concurrent.locks.{ReentrantReadWriteLock, ReentrantLock}
import java.util.concurrent.atomic.AtomicReference

package object synchronizers {

  final def compareAndSet[T](ref: AtomicReference[T])(fun: (T) => T): Unit = {
    var cas = false
    do {
      val c = fun
      val or = ref.get()
      cas = ref.compareAndSet(or, fun(or))
    } while (cas == false)
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
