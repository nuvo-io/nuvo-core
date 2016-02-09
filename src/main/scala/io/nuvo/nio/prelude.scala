package io.nuvo.nio

import scala.language.implicitConversions

package object prelude {


  import java.nio.ByteBuffer
  import java.io.{InputStream, OutputStream}

  implicit def RawBuffer2ByteBuffer(rb: RawBuffer) = rb.buffer
  // implicit def ByteBuffer2RawBuffer(bb: ByteBuffer) = new RawBuffer(bb)

  implicit val allocator = SimpleBufferAllocator
  // implicit val allocator = CircularBufferAllocator

  //val STREAMS_REFRESH_COUNT = 2500

  /**
   * A [[java.nio.ByteBuffer]] backed [[java.io.InputStream]]
   *
   * @param buffer the [[java.nio.ByteBuffer]]
   */
  implicit class ByteBufferInputStream(val buffer: ByteBuffer) extends InputStream {

    override def available() = buffer.limit() - buffer.position()

    override def mark(pos: Int) = buffer.mark()

    override def reset() = buffer.reset()

    override def markSupported = true

    override def skip(n: Long) = {
      val cpos = buffer.position()
      buffer.position(cpos + n.toInt)
      buffer.position() - cpos
    }

    def read(): Int = buffer.get()

    override def read(b: Array[Byte], start: Int, len: Int) = {
      val p = buffer.position()
      val limit = buffer.limit()
      if (p < limit) {
        val available = limit - p
        var length = if (len > available) available else len
        buffer.get(b, start, length)
        length
      }
      else -1
    }

    override def read(b: Array[Byte]) = read(b, 0, b.length)

    @inline
    def getInt(): Int = buffer.getInt()

    @inline
    def get(): Byte = buffer.get()

  }

  implicit class ByteBufferOutputStream(val buffer: ByteBuffer) extends OutputStream {
    @inline
    override def write(b: Array[Byte]) {
      buffer.put(b)
    }

    @inline
    override def write(b: Array[Byte], off: Int, len: Int) {
      buffer.put(b, off, len)
    }

    override def flush() {

    }

    override def close() {

    }

    @inline
    def write(b: Int) {
      val d: Byte = (b & 0x000000ff).toByte
      buffer.put(d)
    }

    @inline
    def put(b: Byte): ByteBufferOutputStream = {
      buffer.put(b)
      this
    }

    @inline
    def putInt(i: Int): ByteBufferOutputStream = {
      buffer.putInt(i)
      this
    }
  }


}
