package io.nuvo.concurrent

/**
 * This class represents an abstract <code>Service</code> which can
 * be started and stopped.
 */

trait Service {
  def start(): Unit
  def stop(): Unit
}
