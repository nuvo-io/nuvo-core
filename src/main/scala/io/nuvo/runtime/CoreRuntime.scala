package io.nuvo.runtime

import io.nuvo.runtime.Config._

object CoreRuntime {
  lazy val log = new Logger("nuvo-core")

//  lazy val domainConfiguration: DomainConf = {
//    val config = Eval(nuvoConfigFile).asObject[DomainConf]()
//    config
//  }
}
