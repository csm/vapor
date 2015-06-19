package vapor.asgard.model

import java.net.InetAddress

import dispatch._
import net.liftweb.json._
import vapor.core.model.LoadBalancerState.LoadBalancerState
import vapor.core.model._
import vapor.util.CachedValue

import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration._

class AsgardObject()(implicit val config:AsgardConfig, implicit val context: ExecutionContext) {
  override def toString: String = getClass.getCanonicalName.split("\\.").last
}

class AsgardEnvironment(val name: String)(implicit override val config:AsgardConfig,
                                          implicit override val context: ExecutionContext)
  extends AsgardObject with Environment {
  lazy val _datacenters = CachedValue[List[DataCenter]](
    Http(url(s"${config.baseurl}/region/list.json") OK as.String)
      .map(s => parse(s) match {
      case l:JArray => l.arr.map {
        case o:JObject => o \ "code" match {
          case s: JString => s.values
        }
      }
    }).map(l => l.map(dc => new AsgardDataCenter(dc, this))), 5.minutes)

  override def dataCenters: Future[List[DataCenter]] = _datacenters.get

  override def toString: String = s"${super.toString}($name)"
}

class AsgardDataCenter(val name: String, environment: AsgardEnvironment)
                      (implicit override val config:AsgardConfig,
                       implicit override val context: ExecutionContext)
  extends AsgardObject with DataCenter {
  lazy val _applications = CachedValue[List[Application]](
    Http(url(s"${config.baseurl}/$name/application/list.json") OK as.String)
      .map(s => parse(s) match {
      case l:JArray => l.arr.map {
        case o:JObject => o \ "name" match {
          case s: JString => s.values
        }
      }
    }).map(l => l.map(app => new AsgardApplication(app, this))), 5.minutes)

  override def applications: Future[List[Application]] = _applications.get

  override def application(appname: String): Future[Application] =
    Http(url(s"${config.baseurl}/${name}/application/${appname}.json") OK as.String)
      .map(_ => new AsgardApplication(appname, this))

  override def toString: String = s"${super.toString}($name)"
}

class AsgardApplication(val name: String, val dataCenter: DataCenter)
                       (implicit override val config:AsgardConfig,
                        implicit override val context: ExecutionContext)
  extends AsgardObject with Application {
  lazy val _appinfo = CachedValue[JObject](Http(url(s"${config.baseurl}/${dataCenter.name}/application/show/$name.json") OK as.String)
    .map(s => parse(s) match {
      case o:JObject => o
    }),
  5.minutes)

  override def clusters: Future[List[Cluster]] = _appinfo.get.map(o => o \ "clusters" match {
    case l:JArray => l.arr.map {
      case s:JString => new AsgardCluster(s.values, this)
    }
  })

  override def toString: String = s"${super.toString}($name, $dataCenter)"
}

class AsgardCluster(val name: String, val application: AsgardApplication)
                   (implicit override val config: AsgardConfig,
                    implicit override val context: ExecutionContext)
  extends AsgardObject with Cluster {
  lazy val _clusterInfo = CachedValue[JArray](Http(url(s"${config.baseurl}/${application.dataCenter.name}/cluster/show/$name.json") OK as.String)
    .map(s => parse(s) match {
      case l:JArray => l
    }), config.cacheTimeout)

  override def scalingGroups: Future[List[ScalingGroup]] = _clusterInfo.get.map(_.arr.map {
    case o:JObject => o \ "autoScalingGroupName" match {
      case s:JString => new AsgardScalingGroup(s.values, this)
    }
  })

  override def loadBalancers: Future[List[LoadBalancer]] = _clusterInfo.get.map(_.arr.flatMap {
    case o:JObject => o \ "loadBalancerNames" match {
      case l:JArray => l.arr.map {
        case s:JString => new AsgardLoadBalancer(s.values, this)
      }
    }
  })

  override def toString: String = s"${super.toString}($name, $application)"
}

class AsgardScalingGroup(val name: String, val cluster: AsgardCluster)
                        (implicit override val config: AsgardConfig,
                         implicit override val context: ExecutionContext)
  extends AsgardObject with ScalingGroup {
  override def instances: Future[List[Instance]] = Future.successful(List())

  override def toString: String = s"${super.toString}($name, $cluster)"
}

class AsgardLoadBalancer(val name: String, val cluster: AsgardCluster)
                        (implicit override val config: AsgardConfig,
                         implicit override val context: ExecutionContext)
  extends AsgardObject with LoadBalancer {

  lazy val _lbInfo = CachedValue[JObject](Http(url(s"${config.baseurl}/${cluster.application.dataCenter.name}/loadBalancer/show/$name.json") OK as.String)
    .map(s => parse(s) match {
      case o:JObject => o
    }), config.cacheTimeout)

  override def hostname: Future[String] = _lbInfo.get.map(o => o \ "loadBalancer" \ "DNSName" match {
    case s:JString => s.values
  })

  override def addresses: Future[List[InetAddress]] = Future.failed(new UnsupportedOperationException)

  override def instances: Future[List[Instance]] = _lbInfo.get.map { _ \ "loadBalancer" \ "instances" match {
    case a:JArray => a.arr.map {
      case o:JObject => o \ "instanceId" match {
        case s:JString => new AsgardInstance(s.values, cluster, Some(this))
      }
    }
  }}

  override def status: Future[Map[String, LoadBalancerState]] = _lbInfo.get.map { _ \ "instanceStates" match {
    case a:JArray => a.arr.map {
      case o:JObject => (o \ "instanceId", o \ "state") match {
        case (id:JString, state:JString) => id.values -> LoadBalancerState(state.values)
      }
    }.toMap
  }}

  override def toString: String = s"${super.toString}($name, $cluster)"
}

class AsgardInstance(val name: String, val cluster: AsgardCluster, val loadBalancer: Option[AsgardLoadBalancer] = None)
                    (implicit override val config: AsgardConfig, implicit override val context: ExecutionContext)
  extends AsgardObject with Instance {
  lazy val _instance = CachedValue[JObject](Http(url(s"${config.baseurl}/${cluster.application.dataCenter.name}/instance/show/$name.json") OK as.String)
    .map(s => parse(s) match {
      case o:JObject => o
    }), config.cacheTimeout)

  override def hostname: Future[String] = _instance.get.map { o => o \ "privateDnsName" match {
    case s:JString => s.values
  }}

  override def loadBalancers: Future[List[LoadBalancer]] = _instance.get.map { _ \ "loadBalancers" match {
    case l:JArray => l.arr.map {
      case o:JObject => o \ "loadBalancerName" match {
        case s:JString => new AsgardLoadBalancer(s.values, cluster)
      }
    }
  }}

  override def loadBalancerState: Future[Map[String, LoadBalancerState]] = for {
    loadBalancers <- loadBalancers
    lbStates <- Future.sequence(loadBalancers.map { lb => lb.status })
  } yield lbStates.map { lbState => name -> lbState.getOrElse(name, LoadBalancerState.NoLoadBalancer) }.toMap

  override def privateAddresses: Future[List[InetAddress]] = _instance.get.map { _ \ "instance" \ "networkInterfaces" \ "privateIpAddresses" match {
    case l:JArray => l.arr.map {
      case o:JObject => o \ "privateIpAddress" match {
        case s:JString => InetAddress.getByName(s.values)
      }
    }
    case JNothing | JNull => List()
  }}

  override def publicAddresses: Future[List[InetAddress]] = _instance.get.map { _ \ "instance" \ "networkInterfaces" \ "publicIpAddresses" match {
    case l:JArray => l.arr.map {
      case o:JObject => o \ "publicIpAddress" match {
        case s:JString => InetAddress.getByName(s.values)
      }
    }
  }}
}