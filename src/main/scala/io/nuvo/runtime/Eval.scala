package io.nuvo.runtime
/*
import scala.reflect.runtime.universe._
import reflect.runtime._
import scala.reflect.runtime.{currentMirror => cm}
import scala.tools.reflect.ToolBox
import java.io.{FileInputStream, File}
import io.BufferedSource
*/

case class Eval(file: String) {
  def asObject[T](): T = ???
}
/*
case class Eval(file: String) {
  def asObject[T](): T = {
    val toolbox = cm.mkToolBox()

    val ifile = new File(file)
    val istream = new FileInputStream(ifile)
    val src = new BufferedSource(istream)

    val code: String = ("" /: src.getLines())(_ + "\n" + _)

    val obj = toolbox.eval(toolbox.parse(code))

    obj.asInstanceOf[T]
  }
}
*/
