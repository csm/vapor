package vapor.aws.model

import dispatch._
import net.liftweb.json._
import vapor.core.model._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AsgardObject(implicit val config:AsgardConfig)

class AsgardEnvironment(val name: String) extends AsgardObject with Environment {
  override def dataCenters: List[DataCenter] = Await.result(
    Http(url(s"${config.baseurl}/regions/list.json") OK as.String)
      .map(s => parse(s) match {
        case l:JArray => l.arr.map {
          case s:JString => s.values
        }
        case _ => throw new IllegalArgumentException("expected a list of regions")
      }).map(l => l.map(dc => new AsgardDataCenter(dc))), Duration.Inf)
}

class AsgardDataCenter(val name: String) extends AsgardObject with DataCenter {
  override def applications: List[Application] = List()

  override def application(name: String): Option[Application] = None
}