package nuvo.nio

import prelude._
import java.io.{ObjectOutputStream, ObjectInputStream, InvalidObjectException}
import java.lang.reflect.Method
import nuvo.core.Tuple
import nuvo.concurrent.synchronizers._
import java.util.concurrent.locks.ReentrantReadWriteLock
import nuvo.runtime.Config._


object ByteOrder {
  def nativeOrder = java.nio.ByteOrder.nativeOrder() match {
    case java.nio.ByteOrder.BIG_ENDIAN => BigEndian
    case java.nio.ByteOrder.LITTLE_ENDIAN => LittleEndian
  }

  val littleEndian = LittleEndian

  val bigEndian = BigEndian
}

sealed abstract class ByteOrder {
  val value: Byte
}

case object LittleEndian extends ByteOrder {
  val value: Byte = 0x30
}
case object BigEndian extends ByteOrder {
  val value: Byte = 0x31
}

object SerializerCache {
  private val mapRWLock = new ReentrantReadWriteLock()

  private var hashMap = Map[(Long, Long), ((Option[Method], Option[Method]), (Option[Method], Option[Method]))]()
  private var map = Map[String, ((Option[Method], Option[Method]), (Option[Method], Option[Method]))]()


  def registerType[T](classT: Class[T], helperClass: Class[_]) {
    val hashTypeMethod = helperClass.getMethods().find(_.getName == "typeHash")
    val hashType = hashTypeMethod.map(_.invoke(null).asInstanceOf[(Long, Long)])

    val serializer = helperClass.getMethods().find(_.getName == "serializeNuvoSF")
    val deserializer = helperClass.getMethods().find(_.getName == "deserializeNoHeaderNuvoSF")

    val nakedKeySerializer = helperClass.getMethods().find(_.getName == "serializeNakedKeyNuvoSF")
    val keyDeserializer = helperClass.getMethods().find(_.getName == "deserializeKeyNoHeaderNuvoSF")

    synchronizedWrite(mapRWLock) {
      val serializers = ((serializer, deserializer), (nakedKeySerializer, keyDeserializer))
      map = map +  (classT.getName -> serializers)
      hashType map { h =>
        println(s"Registering hashType: $h")
        hashMap = hashMap + (h -> serializers)
      }
    }
  }

  def registerType(name: String) {
    val classT = Class.forName(name)
    val helperClass = Class.forName(name + "Helper")
    this.registerType(classT, helperClass)
  }

  @inline
  final def lookup(typeName: String) = synchronizedRead(mapRWLock) { map.get(typeName) }

  @inline
  final def lookup(typeHash: (Long, Long)) = synchronizedRead(mapRWLock) { hashMap.get(typeHash) }

}

object RawBuffer {
  private val MIN_SIZE = 128
  def allocateDirect(size: Int)(implicit allocator: BufferAllocator) = {
    val bs = if (size < MIN_SIZE) MIN_SIZE else size
    allocator.allocateDirect(bs)
  }
  def allocate(size: Int)(implicit allocator: BufferAllocator) = {
    val bs = if (size < MIN_SIZE) MIN_SIZE else size
    allocator.allocate(bs)
  }
}

/**
 * This class provides an an efficient way of writing primitive types
 * as well as serializable objects.
 */
class RawBuffer(val buffer: java.nio.ByteBuffer) extends Ordered[RawBuffer] {

  private val thisClass = this.getClass
  private val jodCount = new java.util.concurrent.atomic.AtomicInteger(0)

  val bis = ByteBufferInputStream(buffer)
  private var (oos, ois) = initObjectStreams()

  private def initObjectStreams() = {
    val i = new Integer(0)
    val oos = new java.io.ObjectOutputStream(buffer)
    oos.writeObject(i)
    oos.flush()
    buffer.flip()
    val ois = new ObjectInputStream(buffer)
    ois.readObject()
    buffer.clear()
    (oos, ois)
  }


  /**
   * @return Returns the array that backs this buffer, if available.
   */
  def array: Option[Array[Byte]] = if (buffer.hasArray()) Some(buffer.array) else None

  /**
   * @return Returns the offset within this buffer's backing array of the
   *         first element of the buffer, if available.
   */
  def arrayOffset: Int = buffer.arrayOffset

  /**
   * @return Returns this buffer's capacity.
   */
  def capacity: Int = buffer.capacity()

  /**
   * Clears this buffer.
   *
   * @return this buffer
   */
  def clear() : RawBuffer = {
    buffer.clear()
    this
  }

  /**
   * When reading object with Java Serialization, the only way to release
   * objects is to close the stream. This is a costly operation, but required
   * in order to avoid memory leaks.
   *
   * As such the raw-buffer keeps a count of the number of objects being read
   * and periodically clears the streams by closing and reallocating.
   *
   * @return this buffer
   */
  def resetStreams() {
    buffer.clear()
    ois.close()
    oos.close()
    oos = new ObjectOutputStream(buffer)
    oos.writeObject(new Integer(0))
    buffer.flip()
    ois = new ObjectInputStream(buffer)
    ois.readObject()
    buffer.clear()
  }

  /**
   * Flips this buffer.
   *
   * @return this buffer
   */
  def flip(): RawBuffer = {
    buffer.flip()
    this
  }


  /**
   * Tells whether there are any elements between the current position and the limit.
   *
   * @return true if the buffer has remaining space.
   */
  def hasRemaining: Boolean = buffer.hasRemaining

  /**
   * Tells whether or not this buffer is direct.
   *
   * @return true if the buffer is direct.
   */
  def direct: Boolean = buffer.isDirect

  /**
   * Tells whether or not this buffer is read-only.
   *
   * @return true if the buffer is read-only.
   */
  def readOnly: Boolean = buffer.isReadOnly

  /**
   * Returns this buffer's limit.
   *
   * @return the buffer limit.
   */
  def limit: Int = buffer.limit

  /**
   * Sets this buffer's limit.
   *
   * @param newLimit the new limit.
   * @return this buffer.
   */
  def limit(newLimit: Int): RawBuffer = {
    buffer.limit(newLimit)
    this
  }

  /**
   * Sets this buffer's mark at its position.
   *
   * @return this buffer
   */
  def mark(): RawBuffer = {
    buffer.mark()
    this
  }

  /**
   * Returns this buffer's position.
   *
   * @return the buffer position.
   */
  def position: Int = buffer.position()

  /**
   * Sets this buffer's position.
   *
   * @param newPosition the new buffer position.
   * @return this buffer.
   */
  def position(newPosition: Int): RawBuffer = {
    buffer.position(newPosition)
    this
  }

  /**
   * Returns the number of elements between the current position and the limit.
   *
   * @return the number of elements between the current position and the limit.
   */
  def remaining: Int ={
    buffer.remaining()
  }

  /**
   * Resets this buffer's position to the previously-marked position.
   *
   * @return this buffer
   */
  def reset(): RawBuffer = {
    buffer.reset()
    this
  }

  /**
   * Rewinds this buffer.
   *
   * @return this buffer.
   */
  def rewind(): RawBuffer = {
    buffer.rewind()
    this
  }

  /**
   * Creates a new byte buffer whose content is a shared subsequence of this buffer's content.
   * The content of the new buffer will start at this buffer's current position. Changes to this
   * buffer's content will be visible in the new buffer, and vice versa; the two buffers' position,
   * limit, and mark values will be independent.
   * The new buffer's position will be zero, its capacity and its limit will be the number of bytes
   * remaining in this buffer, and its mark will be undefined. The new buffer will be direct if,
   * and only if, this buffer is direct, and it will be read-only if, and only if, this buffer is read-only.
   *
   * @return The new byte buffer
   */
  def slice: RawBuffer = new RawBuffer(buffer.slice())

  /**
   * Creates a new byte buffer that shares this buffer's content.
   *
   * The content of the new buffer will be that of this buffer.
   * Changes to this buffer's content will be visible in the new buffer,
   * and vice versa; the two buffers' position, limit, and mark values will be independent.
   *
   * The new buffer's capacity, limit, position, and mark values will be identical to those of this buffer.
   * The new buffer will be direct if, and only if, this buffer is direct, and it will be read-only if,
   * and only if, this buffer is read-only.
   *
   * @return The new byte buffer
   */
  def duplicate: RawBuffer = new RawBuffer(buffer.duplicate())

  /**
   * Creates a new, read-only byte buffer that shares this buffer's content.
   *
   * The content of the new buffer will be that of this buffer. Changes to
   * this buffer's content will be visible in the new buffer; the new buffer itself,
   * however, will be read-only and will not allow the shared content to be modified.
   * The two buffers' position, limit, and mark values will be independent.
   *
   * The new buffer's capacity, limit, position, and mark values will be identical to those of this buffer.
   * If this buffer is itself read-only then this method behaves in exactly the same way as the duplicate method.
   *
   * @return The new, read-only byte buffer
   */
  def asReadOnlyBuffer: RawBuffer = new RawBuffer(buffer.asReadOnlyBuffer())


  /**
   * Relative get method. Reads the byte at this buffer's current position, and then increments the position.
   *
   * @return The byte at the buffer's current position
   * @throws  java.nio.BufferUnderflowException - If the buffer's current position is not smaller than its limit
   */
  def get(): Byte = buffer.get

  def getByte() = get()

  /**
   * Relative put method  (optional operation).
   *
   *  Writes the given byte into this buffer at the current position, and then increments the position.
   *
   * @param b  The byte to be written
   * @return   This buffer
   */
  def  put(b: Byte): RawBuffer = {
    buffer.put(b)
    this
  }

  def putByte(b: Byte): RawBuffer = put(b)

  /**
   * Absolute get method. Reads the byte at the given index.
   *
   * @param index The index from which the byte will be read
   * @return The byte at the given index
   */
  def get(index: Int) = buffer.get(index)

  def getByte(index: Int) = get(index)

  /**
   * Absolute put method  (optional operation).
   *
   * Writes the given byte into this buffer at the given index.
   *
   * @param index The index at which the byte will be written
   * @param b The byte value to be written
   * @return This buffer
   */
  def put(index: Int, b: Byte): RawBuffer = {
    buffer.put(index, b)
    this
  }

  def putByte(index: Int, b: Byte) = put(index, b)

  def get(dest: Array[Byte], offset: Int, length: Int) = {
    buffer.get(dest, offset, length)
    this
  }

  def get(dest: Array[Byte]): RawBuffer = {
    buffer.get(dest)
    this
  }

  def put(src: RawBuffer): RawBuffer = {
    buffer.put(src.buffer)
    this
  }


  def put(src: Array[Byte], offset: Int, length: Int): RawBuffer = {
    buffer.put(src, offset, length)
    this
  }

  def put(src: Array[Byte]): RawBuffer = {
    buffer.put(src)
    this
  }

  override def toString = buffer.toString

  def compare(that: RawBuffer): Int = buffer.compareTo(that.buffer)


  def order: ByteOrder = buffer.order match {
    case java.nio.ByteOrder.BIG_ENDIAN => BigEndian
    case java.nio.ByteOrder.LITTLE_ENDIAN => LittleEndian
  }


  def order(bo: ByteOrder): RawBuffer = bo match {
    case BigEndian => buffer.order(java.nio.ByteOrder.BIG_ENDIAN); this
    case LittleEndian => buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN); this
  }

   def getChar() = buffer.getChar

  def putChar(ch: Char): RawBuffer = {
    buffer.putChar(ch)
    this
  }

   def getChar(index: Int): Char = buffer.getChar(index)

  def putChar(index: Int, ch: Char): RawBuffer = {
    buffer.putChar(index, ch)
    this
  }

  def getShort() = buffer.getShort()

  def putShort(s: Short): RawBuffer = {
    buffer.putShort(s)
    this
  }


  def getShort(index: Int): Short = buffer.getShort

  def putShort(index: Int, s: Short): RawBuffer = {
    buffer.putShort(index, s)
    this
  }


  def getInt() = buffer.getInt

  def putInt(i: Int) = {
    buffer.putInt(i)
    this
  }

  def getInt(index: Int) = buffer.getInt(index)

  def putInt(index: Int, i: Int) = {
    buffer.putInt(index, i)
    this
  }


  def getLong() = buffer.getLong

  def putLong(l: Long) = {
    buffer.putLong(l)
    this
  }

  def getLong(index: Int) = buffer.getLong(index)

  def putLong(index: Int, l: Long) = {
    buffer.putLong(index, l)
    this
  }


  def getFloat() = buffer.getFloat

  def putFloat(v: Float) = {
    buffer.putFloat(v)
    this
  }

  def getFloat(index: Int) = buffer.getFloat(index)

  def putFloat(index: Int, v: Float) = {
    buffer.putFloat(index, v)
    this
  }


  def getDouble() = buffer.getDouble

  def putDouble(v: Double) = {
    buffer.putDouble(v)
    this
  }

  def getDouble(index: Int) = buffer.getDouble(index)

  def putDouble(index: Int, v: Double) = {
    buffer.putDouble(index, v)
    this
  }

  def putString(s: String) = {
    putInt(s.length)
    put(s.getBytes)
  }

  def getString() = {
    val len = getInt()
    val buf = new Array[Byte](len)
    get(buf)
    new String(buf)
  }

  def putObject[T <: AnyRef](o: T, sf: SerializationFormat): RawBuffer = sf match {
    case NuvoSF => {
      val typeName = o.getClass.getName

      val (oserializers, kserializers) = SerializerCache.lookup(typeName).getOrElse (
        {
          SerializerCache.registerType(typeName)
          SerializerCache.lookup(typeName).get
        })

      oserializers._1.map(_.invoke(null, this, o))
      this
    }
    case JavaSF => {
      // The code below may look a bit mysterious. Yet, to avoid memory leaks
      // on the reader side we need to (1) reset the stream, and (2) ensure
      // that the reset market is read. To do so, we write an extra byte that
      // is read on the reader side to force the consumption of the reset marker.
      oos.writeObject(o)
      oos.reset()
      oos.writeByte(0)
      oos.flush()
      this
    }
    case _ => throw new UnsupportedOperationException(sf + " not supported yet")
  }

  def putObject[T <: AnyRef](o: T): RawBuffer = putObject(o, NuvoSF)

  def getObject[T <: AnyRef](): T = getObject[T](NuvoSF)

  def getObject[T <: AnyRef](sf: SerializationFormat): T = sf match {
    case NuvoSF => {
      val currentOder = this.order
      this.order(LittleEndian)
      val header = this.getInt()
      val E = header >> 24
      val len = header & 0x00ffffff

      this.order( E match {
        case LittleEndian.value => LittleEndian
        case BigEndian.value => BigEndian
        case _ => {
          try {
            this.position(this.position + len)
          } catch {
            case iea: IllegalArgumentException => println(s">> Invalid length: $len")
            throw iea
          }
          throw new InvalidObjectException("The header is not valid")
        }
      })
      val startPosition = buffer.position
      // val typeName = getString()
      val typeHash = (getLong(), getLong())
      // log.debug(s"TypeHash = $typeHash")
      val (oserializer, kdeserializer) = SerializerCache.lookup(typeHash).getOrElse (
      {
        throw new RuntimeException(s"Unable to deserialize type $typeHash. Ensure all types are properly registered")
      })

      val result = oserializer._2.map(_.invoke(null, this).asInstanceOf[T]).get
      this.position(startPosition + len)
      this.order(currentOder)
      result
    }
    case JavaSF => {
      val res = ois.readObject().asInstanceOf[T]
      /*
      if (jodCount.incrementAndGet() > STREAMS_REFRESH_COUNT) {
        resetStreams()
      }
      */
      // force the consumption of the reset marker
      ois.readByte()
      res
    }

    case _ => throw new UnsupportedOperationException(sf + " not supported yet")
  }

  /**
   * Write the key of an tuple of type T on the buffer.
   * @param k the key to be written
   * @tparam T the tuple type
   */

  def putKey[T <: Tuple : Manifest](k: Any) = {
    val typeName = manifest[T].runtimeClass.getName

    val (oserializers, kserializers) = SerializerCache.lookup(typeName).getOrElse(
    {
      SerializerCache.registerType(typeName)
      SerializerCache.lookup(typeName).get
    })

    kserializers._1.map(_.invoke(null, this, k.asInstanceOf[AnyRef]))
    this
  }


  /**
   * Get the key for type T
   * @tparam K
   * @return the key for type T
   */
  def getKey[K]() = {
    val currentOder = this.order
    this.order(LittleEndian)
    val header = this.getInt()
    val E = header >> 24
    val len = header & 0x00ffffff

    this.order( E match {
      case LittleEndian.value => LittleEndian
      case BigEndian.value => BigEndian
      case _ => {
        this.position(this.position + len)
        throw new InvalidObjectException("The header is not valid")
      }
    })
    val startPosition = buffer.position
    val typeName = getString()

    val (oserializers, kserializers) = SerializerCache.lookup(typeName).getOrElse (
    {
      SerializerCache.registerType(typeName)
      SerializerCache.lookup(typeName).get
    })

    val result = kserializers._2.map(_.invoke(null, this).asInstanceOf[K]).get
    this.position(startPosition + len)
    this.order(currentOder)
    result
  }

}
