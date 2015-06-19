package vapor.asgard.model

import scala.concurrent.duration._

case class AsgardConfig(baseurl: String, cacheTimeout: FiniteDuration = 5.minutes)
