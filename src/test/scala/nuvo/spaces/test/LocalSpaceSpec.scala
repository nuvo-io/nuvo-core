/*
package nuvo.spaces.test

import org.scalatest._

import nuvo.core.Tuple
import nuvo.spaces.Space
import nuvo.spaces.prelude.LocalSpace._

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