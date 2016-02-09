package io.nuvo.runtime

import java.net.InetAddress

object Config {

  object Concurrency {
    val threadPoolSize = 2
    val threadPoolQueueSize = 16
  }

  object Networking {

    val baseDataPort = 9900
    val baseDiscoveryPort = 9901
    val domainGain = 10
    val defaultDataLocator = "io.nuvo:protostuff/udp:239.255.1.1:9000"
    val defaultDiscoveryLocator = "io.nuvo:protostuff/udp:239.255.1.1:9001"
    val defaultMcastAddres = InetAddress.getByName("239.255.1.1")

    val defaultBufferSize = 4096
    val defaultBufferCacheSize = 32

    val defaultNIC = "en1"

    object Socket {
      val TCP_NO_DELAY = true
      val Performance = (
        0, // Connection time
        1, // Latency
        2 // Throughput
      )
      val SendBufSize = 1048576 // 1MB

    }
  }
  // -- Logging
  type Logger = io.nuvo.util.log.ConsoleLogger
  val log = new io.nuvo.util.log.ConsoleLogger("nuvola-core")

  // -- Configuration File
  val nuvoConfigFile = System.getProperty("user.home") + "/.nuvo.cfg"


}
