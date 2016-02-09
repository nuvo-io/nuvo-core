package io.nuvo.util.log

import annotation.elidable
import annotation.elidable._

trait Logger {
  @elidable(10) def debug(msg: String)
  @elidable(15) def log(msg: String)

  @elidable(20) def warning(msg: String)

  @elidable(25) def error(msg: String)
}

class ConsoleLogger(val source: String) extends Logger {
  val blue = Console.BLUE
  val green = Console.GREEN
  val yellow = Console.YELLOW_B
  val red = Console.RED_B
  val magenta = Console.MAGENTA
  val reset = Console.RESET

  @inline
  @elidable(FINER) final def trace(msg: String): Unit =
    println(s"$blue [$source | Trace]:  $msg $reset")

  @inline
  @elidable(FINE) final def debug(msg: String): Unit =
    println(s"$blue [$source | Debug]:  $msg $reset")

  @inline
  @elidable(INFO) final def log(msg: String): Unit =
    println(s"$green [$source | Log]:  $msg $reset")

  @inline
  @elidable(INFO) final def info(msg: String): Unit =
    println(s"$green [$source | Log]:  $msg $reset")

  @inline
  @elidable(WARNING) final def warning(msg: String): Unit =
    println(s"$yellow [$source | Warning]:  $msg $reset")

  @inline
  @elidable(SEVERE) final def error(msg: String): Unit =
    println(s"$red [$source | Error]:  $msg $reset")

}
