package nuvo.concurrent.atomic


import java.util.concurrent.atomic.AtomicReference


object AtomicList {
  def apply[T]() = new AtomicList[T](List[T]())
  def apply[T](l: List[T]) = new AtomicList(l)
}

final class AtomicList[T](l: List[T]) {
  val aref = new AtomicReference[List[T]](l)

  def :: (e: T): List[T] = {
    var cas = false
    do {
      val l = aref.get()
      val nl = e :: l
      cas = aref.compareAndSet(l, nl)
    } while (cas == false)
    aref.get()
  }

}