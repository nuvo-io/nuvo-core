/*
package io.nuvo.spaces.test

import org.scalatest._

import io.nuvo.core.Tuple
import io.nuvo.spaces.Space
import io.nuvo.spaces.prelude.LocalSpace._

case class TestTuple(id: String, value: Long) extends Tuple {
  lazy val key = id
}

class LocalSpaceSpec extends FlatSpec {

  "A write" should "insert an element in the tuple space" in {

      val space = Space[TestTuple]()

      space write (TestTuple("first", 1))

  }

}

*/
