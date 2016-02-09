package io.nuvo.core

/**
 * This is a utility class that can be used to define
 * keys with io.nuvo types
 */
object KeyList {
  def apply() = None
  def apply[T1](t1: T1) = t1
  def apply[T1, T2](t1: T1, t2: T2) = (t1, t2)
  def apply[T1, T2, T3](t1: T1, t2: T2, t3: T3) = (t1, t2, t3)
  def apply[T1, T2, T3, T4](t1: T1, t2: T2, t3: T3, t4: T4) = (t1, t2, t3, t4)
  def apply[T1, T2, T3, T4, T5](t1: T1, t2: T2, t3: T3, t4: T4, t5: T5) = (t1, t2, t3, t4, t5)
  def apply[T1, T2, T3, T4, T5, T6](t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6) = (t1, t2, t3, t4, t5, t6)
  def apply[T1, T2, T3, T4, T5, T6, T7](t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7) = (t1, t2, t3, t4, t5, t6, t7)
  def apply[T1, T2, T3, T4, T5, T6, T7, T8](t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8) = (t1, t2, t3, t4, t5, t6, t7, t8)
  def apply[T1, T2, T3, T4, T5, T6, T7, T8, T9](t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9) = (t1, t2, t3, t4, t5, t6, t7, t8, t9)
  def apply[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10) = (t1, t2, t3, t4, t5, t6, t7, t8, t9, t10)
}
