package io.nuvo.concurrent

import scala.collection.immutable.Queue


/**
 * This class represents a communication channel where a producer can
 * <code>put</code> elements and a consumer can <code>take</code> them out.
 *
 * @author Angelo Corsaro
 * @version 1.0
 */

trait Channel[E] extends Iterable[E] {
  def put(e: E)
  def take(): E
  def poll(): Option[E]
  def takeAll(): Queue[E]
}
