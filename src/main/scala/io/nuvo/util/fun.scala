package io.nuvo.util

import scala.util.{Failure, Success, Try}

package object fun {
  def safeF[T, Q](f: T => Q)(t: T): Try[Q] = try {
    val r = f(t)
    Success(r)
  } catch {
    case t: Throwable => Failure(t)
  }
}
