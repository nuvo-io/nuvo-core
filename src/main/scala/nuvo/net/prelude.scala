package nuvo.net

import nuvo.nio.{BigEndian, LittleEndian, RawBuffer}
import java.nio.channels.{SocketChannel, DatagramChannel}
import nuvo.nio.prelude._
import java.io.IOException

package object prelude {
  val udpSelector = (channel: DatagramChannel, buf: RawBuffer) => {
    buf.clear()
    channel.receive(buf)
    buf.flip()
    buf
  }

  val tcpNuvoSelector = (channel: SocketChannel, buf: RawBuffer) => {

    buf.clear()
    buf.order(LittleEndian)
    val hbpos = buf.capacity - 4
    buf.position(hbpos)

    do {
      if(channel.read(buf)  == -1) throw new IOException("Channel Closed by Peer")
    } while (buf.position != buf.capacity)

    buf.position(hbpos)
    val MEL = buf.getInt()
    val E = MEL >> 24

    val length = MEL & 0x00ffffff

    val endianess = E match {
      case LittleEndian.value => {

        LittleEndian
      }
      case BigEndian.value => {
        BigEndian
      }
      case _ => {
        channel.close()
        throw new IOException("Currupted Stream")
      }
    }

    buf.position(buf.capacity - length - 4)
    buf.putInt(MEL)
    buf.order(endianess)
    do {
      if(channel.read(buf) == -1) throw new IOException("Channel Closed by Peer")
    } while (buf.position != buf.capacity)
    buf.position(buf.capacity - length - 4)
    buf
  }
}
