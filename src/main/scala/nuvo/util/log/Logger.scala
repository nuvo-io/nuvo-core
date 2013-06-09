package nuvo.util.log

import annotation.elidable

trait Logger {
  @elidable(10) def debug(msg: String)
  @elidable(15) def log(msg: String)

  @elidable(20) def warning(msg: String)

  @elidable(25) def error(msg: String)
}

class ConsoleLogger(val source: String) extends Logger {
  @inline @elidable(10) final def debug(msg: String) {
    println(Console.BLUE + ">> ["+ source + " | Debug]: " + msg + Console.RESET) }

  @inline @elidable(15) final def log(msg: String) {
    println(Console.GREEN +">> ["+ source + " | [Log]: " + msg + Console.RESET) }

  @inline @elidable(20) final def warning(msg: String) {
    println(Console.YELLOW_B +">> ["+ source + " | Warning] " + msg + Console.RESET) }

  @inline @elidable(25) final def error(msg: String) {
    println(Console.RED_B +">> ["+ source + " | Error]: " + msg  + Console.RESET) }

}
