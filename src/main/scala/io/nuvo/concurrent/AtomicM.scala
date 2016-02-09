package io.nuvo.concurrent

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import synchronizers.compareAndSet
import scala.concurrent.SyncChannel
import scala.language.higherKinds
/**
 * AtimicM can't be used with function that have side-effects since
 * side effects would be executed several times under concurrency as
 * a consequence of how compareAndSet is implemented.
 *
 * am.map { xs match {
 *   case x :: xs => {
 *       // do something on x
 *       xs
 *   }
 *   case List(_) => do {
 *     wait(NotEmpty)
 *
 *   }
 * }
 *
 */


object AtomicM {
  def apply[A, B](r: B) = new AtomicM[A, B](None, new AtomicReference[B](r))
  def apply[A, B](s: A, r: B) = new AtomicM[A, B](Some(s), new AtomicReference[B](r))
}


object AChannel {
  def apply() = new AChannel(new SyncChannel[Any])
}

class AChannel(private val channel: SyncChannel[Any]) {
  private val count = new AtomicInteger(0)
  def consume[T](): T = {
    count.incrementAndGet()
    channel.read.asInstanceOf[T]
  }

  def produce[T](t: T): Unit = channel.write(t)

  def produceAll[T](t: T): Unit = {
    var c = count.getAndSet(0)
    while (c > 0) {
      channel.write(t)
      c -= 1
    }
  }

}




class AtomicM[A, B](val s: Option[A], private val aref: AtomicReference[B], private val channel: AChannel = AChannel()) {

  final def get() = aref.get()

//  def map(f: (Option[A], B) => (Option[A], B)): AtomicM[A, B] = {
//    var ns = s
//    compareAndSet(aref) { a =>
//      val res = f(s, a)
//      ns = res._1
//      res._2
//    }
//    new AtomicM[A, B](ns, aref, channel)
//  }
//
//  def flatMap(f: (Option[A], B) => AtomicM[A, B]): AtomicM[A, B] = {
//    var ns = s
//    compareAndSet(aref) {a =>
//      val res = f(s, a)
//      ns = res.s
//      res.get()
//    }
//    new AtomicM[A, B](ns, aref, channel)
//  }

  def map(f: (Option[A], B, AChannel) => (Option[A], B, AChannel)): AtomicM[A, B] = {
    var ns = s
    compareAndSet(aref) { a =>
      val res = f(s, a, channel)
      ns = res._1
      res._2
    }
    new AtomicM[A, B](ns, aref, channel)
  }

  def flatMap(f: (Option[A], B, AChannel) => AtomicM[A, B]): AtomicM[A, B] = {
    var ns = s
    compareAndSet(aref) {a =>
      val res = f(s, a, channel)
      ns = res.s
      res.get()
    }
    new AtomicM[A, B](ns, aref, channel)
  }


}

//class AtomicM2[M[_]](val r: Option[M[_]], val sref: AtomicReference[M[_]]) {
//
//  def map[A](a: AtomicM2[M[A]])(f: (Option[M[A]], M[A]) => (Option[M[A]], M[A])): AtomicM2[M[A]] = {
//    var (res, ns): (Option[M[A]], M[A]) = (None, ???)
//    var os: Option[M[A]] = a.r.asInstanceOf[Option[M[A]]]
//    compareAndSet(sref) { s =>
//      // (res, ns) = f (r, s)
//      val er = f (os, s.asInstanceOf[M[A]])
//      ns = er._2
//      res = er._1
//      ns
//    }
//    new AtomicM2[M[A]](res, sref).asInstanceOf[AtomicM2[M[A]]]
//  }
//}

//
//class AtomicM[T](v: T) {
//  private val aref = new AtomicReference[T](v)
//
//  def get() = aref.get()
//
//  /**
//   * The map method is used to modify the state of this AtomicM instance.
//   *
//   * @param f function to atomically apply to the current state
//   * @return this object with the new state
//   */
//  def map(f: T => T): AtomicM[T] = {
//    compareAndSet(aref) {
//      (x) => f (x)
//    }
//    this
//  }
//
//  /**
//   * Injects the content of this AtomicM into another AtomicM.
//   * The state is not changed.
//   *
//   * @param f
//   * @tparam Q
//   * @return
//   */
//  def flatMap[Q](f: T => AtomicM[Q]): AtomicM[Q] = f (aref.get())
//
//}
