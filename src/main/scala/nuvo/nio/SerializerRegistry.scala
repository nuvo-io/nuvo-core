package nuvo.nio

import java.lang.reflect.Method
import nuvo.concurrent.synchronizers._
import java.util.concurrent.atomic.AtomicReference


/**
 * The SerializerCache maintains a cache of serializer/deserializers for types.
 */
class SerializerRegistry {
  import nuvo.runtime.Config.log
  // private val mapRWLock = new ReentrantReadWriteLock()
  private val hashMapRef =
    new AtomicReference(Map[(Long, Long), ((Option[Method], Option[Method]), (Option[Method], Option[Method]))]())

  private val mapRef =
    new AtomicReference(Map[String, ((Option[Method], Option[Method]), (Option[Method], Option[Method]))]())


  def registerType[T](classT: Class[T], helperClass: Class[_]) {
    val hashTypeMethod = helperClass.getMethods().find(_.getName == "typeHash")
    hashTypeMethod.map(_.invoke(null).asInstanceOf[(Long, Long)]).map { hashType => {

      val serializer = helperClass.getMethods().find(_.getName == "serializeNuvoSF")
      val deserializer = helperClass.getMethods().find(_.getName == "deserializeNoHeaderNuvoSF")

      val nakedKeySerializer = helperClass.getMethods().find(_.getName == "serializeNakedKeyNuvoSF")
      val keyDeserializer = helperClass.getMethods().find(_.getName == "deserializeKeyNoHeaderNuvoSF")

      val serializers = ((serializer, deserializer), (nakedKeySerializer, keyDeserializer))

      compareAndSet(hashMapRef) {hashMap =>
        hashMap + (hashType  -> serializers)
      }

      compareAndSet(mapRef) {map =>
        map +  (classT.getName -> serializers)
      }
    }}.getOrElse(log.warning("Unable to register type "+ classT.getName()))
  }

  def registerType(name: String) {
    val classT = Class.forName(name)
    val helperClass = Class.forName(name + "Helper")
    this.registerType(classT, helperClass)
  }

  @inline
  final def lookup(typeName: String) = mapRef.get().get(typeName)

  @inline
  final def lookup(typeHash: (Long, Long)) = hashMapRef.get().get(typeHash)

}
