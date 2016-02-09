package io.nuvo.nio

abstract class SerializationFormat

// NDR: Nuvo Serialization Format
object NuvoSF extends SerializationFormat

//
object JavaSF extends SerializationFormat

// CDR: Common Data Representation
object CdrSF extends SerializationFormat

// JSON: JavaScript Object Notation
object JsonSF extends SerializationFormat

