package nuvo.net

import java.nio.channels.SelectionKey
import nuvo.nio.RawBuffer

object event {

  /**
   * These are the events used to notify specific IO statuses.
   * Notice that this events are objects as the channels for which the
   * events will be raised are usually provided via closures in partial
   * functs. This allows us to minimize memory allocation on the
   * critical path and speed up the matching.
   */
  sealed abstract class ReactorEvent
  case class OnRead(k: SelectionKey, buf: RawBuffer) extends ReactorEvent

  case class OnWrite(k: SelectionKey, buf: RawBuffer) extends ReactorEvent

  case class OnConnect(k: SelectionKey, buf: RawBuffer) extends ReactorEvent

  case class OnAccept(k: SelectionKey, buf: RawBuffer) extends ReactorEvent

}
