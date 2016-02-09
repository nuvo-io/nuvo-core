package io.nuvo.nio

import io.nuvo.runtime.Config._
import java.nio.ByteBuffer

/**
 * Trait for buffer allocators.
 */
trait BufferAllocator {


  /**
   * Allocate a buffer using the configured default size and sets it into native byte order
   * @return the allocated buffer
   */
  def allocate(): RawBuffer

  /**
   * Allocate a buffer optimized for I/O using the configure default size and sets it into native byte order
   * @return the allocated buffer
   */
  def allocateDirect(): RawBuffer

  /**
   * Allocate a buffer and sets it into native byte order
   * @param size the buffer size
   * @return the allocated buffer
   */
  def allocate(size: Int): RawBuffer

  /**
   * Allocate a buffer optimized for I/O and sets it into native byte order
   * @param size the buffer size
   * @return the allocated buffer
   */
  def allocateDirect(size: Int): RawBuffer

  /**
   * May release the resources associated with this buffer.
   *
   * @param buf the buffer to release
   */
  def release(buf: RawBuffer)
}

object SimpleBufferAllocator extends BufferAllocator {


  final def allocate(size: Int): RawBuffer = {
    val buf = new RawBuffer(ByteBuffer.allocate(size))
    buf order(ByteOrder nativeOrder)
  }

  final def allocateDirect(size: Int): RawBuffer = {
    val buf = new RawBuffer(ByteBuffer.allocateDirect(size))
    buf order(ByteOrder nativeOrder)
  }

  final def release(buf: RawBuffer) {}

  /**
   * Allocate a buffer using the configured default size and sets it into native byte order
   * @return the allocated buffer
   */
  final def allocate(): RawBuffer = {
    val buf = new RawBuffer(ByteBuffer.allocate(Networking.defaultBufferSize))
    buf order(ByteOrder nativeOrder)
  }

  /**
   * Allocate a buffer optimized for I/O using the configure default size and sets it into native byte order
   * @return the allocated buffer
   */
  final def allocateDirect(): RawBuffer = {
    val buf = new RawBuffer(ByteBuffer.allocateDirect(Networking.defaultBufferSize))
    buf order(ByteOrder nativeOrder)
  }
}

object CircularBufferAllocator extends BufferAllocator {

  private var bufferList = (1 to Networking.defaultBufferCacheSize).toList map (
    _ => {
      val b = new RawBuffer(ByteBuffer.allocateDirect(Networking.defaultBufferSize))
      b order (ByteOrder nativeOrder)
      b
    })

  /**
   * Allocate a buffer and sets it into native byte order
   * @param size the buffer size
   * @return the allocated buffer
   */
  final def allocate(size: Int): RawBuffer = SimpleBufferAllocator.allocate(size)

  /**
   * Allocate a buffer optimized for I/O and sets it into native byte order
   * @param size the buffer size
   * @return the allocated buffer
   */
  final def allocateDirect(size: Int): RawBuffer = SimpleBufferAllocator.allocateDirect(size)

  /**
   * May release the resources associated with this buffer.
   *
   * @param buf the buffer to release
   */
  def release(buf: RawBuffer) = {
    if (buf.capacity == Networking.defaultBufferSize) synchronized {
      buf.clear()
      buf order  (ByteOrder nativeOrder)
      this.bufferList = buf +: bufferList
    }
  }

  /**
   * Allocate a buffer using the configured default size and sets it into native byte order
   * @return the allocated buffer
   */
  def allocate(): RawBuffer = synchronized {
    bufferList match {
      case x::xs => x
      case List() => SimpleBufferAllocator.allocate(Networking.defaultBufferSize)
    }
  }


  /**
   * Allocate a buffer optimized for I/O using the configure default size and sets it into native byte order
   * @return the allocated buffer
   */
  def allocateDirect(): RawBuffer = synchronized {
    bufferList match {
      case x::xs => x
      case List() => SimpleBufferAllocator.allocateDirect(Networking.defaultBufferSize)
    }
  }
}
