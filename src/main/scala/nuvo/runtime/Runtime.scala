package nuvo.runtime

import nuvo.runtime.Config._
import nuvo.net.Locator

package object prelude {
  def updateLocatorForDomain(l: String, d: Int): String = {
    val locator = Locator(l).map { loc =>
      new Locator(loc.proto, loc.encoding, loc.transport, loc.address, loc.port + (Config.Networking.domainGain * d))
    }
    locator.get.toString
  }
}
object Runtime {
  val domainConfiguration: DomainConf = {
    val config = Eval(nuvoConfigFile).asObject[DomainConf]()
    config
  }
}
