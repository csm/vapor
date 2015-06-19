package vapor.asgard.intTest

import java.util.concurrent.TimeUnit

import org.specs2.Specification
import vapor.asgard.model.{AsgardEnvironment, AsgardConfig}

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

/**
 * Created by cmarshall on 6/17/15.
 */
class ModelSpec extends Specification {
  def is = s2"""
  Specification for Asgard/AWS model.
  """

  val timeout = FiniteDuration(1, "minute")
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val config = AsgardConfig("http://asgard.inf.blackpearlsystems.net")
  val environment = new AsgardEnvironment("inf")
  val dataCenters = Await.result(environment.dataCenters, timeout)
  println(dataCenters)
  val applications = dataCenters.map { dc => dc -> Await.result(dc.applications, timeout) }.toMap
  println(applications)
  val clusters = applications.values.flatMap { l => l.map { app => app -> Await.result(app.clusters, timeout)}}.toMap
  println(clusters)
  val scalingGroups = clusters.values.flatMap { l => l.map { cluster => cluster -> Await.result(cluster.scalingGroups, timeout)}}.toMap
  println(scalingGroups)
  val loadBalancers = clusters.values.flatMap { l => l.map { cluster => cluster -> Await.result(cluster.loadBalancers, timeout)}}.toMap
  println(loadBalancers)
  val instances = loadBalancers.values.flatMap { _.map { loadBalancer => loadBalancer -> Await.result(loadBalancer.instances, timeout)}}.toMap
  println(instances)
  val lbStates = instances.values.map { _.map { instance => Await.result(instance.loadBalancerState, timeout) }}
  println(lbStates)
}
