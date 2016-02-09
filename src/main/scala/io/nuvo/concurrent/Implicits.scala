package io.nuvo.concurrent

import scala.languageFeature.implicitConversions

object Implicits {

  implicit class RichRunnable[T](runner:  () => T) extends Runnable {
    def run() { runner() }
  }

}
