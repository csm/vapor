package vapor.service

import akka.actor.Actor
import spray.routing.HttpService

/**
 */
class VaporRouteActor extends Actor with VaporRoute {
  def actorRefFactory = context
  def receive = runRoute(routes)
}

trait VaporRoute extends HttpService {
  val routes = {
    import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
    import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

    path("/api/v1") {
      get {
        path("apps") { ctx =>
          ctx.complete(Map("someapp" -> Map("someattribute" -> "somevalue")))
        }
      }
    }
  }
}