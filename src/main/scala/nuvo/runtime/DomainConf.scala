package nuvo.runtime

import java.net.InetSocketAddress

abstract class PartitionConf {
  val name: String
  val address: Some[InetSocketAddress]
}

/**
 * A Scala class representing the configuration of a domain
 */
abstract class DomainConf {
  val domainId: Int
  val partitions: List[PartitionConf]
  val alivePeriod: Long
  val dataEndpoints: List[String]
  val discoveryEndpoints: List[String]
}
