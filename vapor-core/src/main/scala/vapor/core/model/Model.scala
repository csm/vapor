/* Model.scala --
 *
 * This file is a part of Vapor.
 *
 * Vapor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Vapor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Vapor.  If not, see <http://www.gnu.org/licenses/>.
 */

package vapor.core.model

import java.net.InetAddress

import scala.concurrent.Future

trait Environment {
  def name:String
  def dataCenters:Future[List[DataCenter]]
}

trait DataCenter {
  def name:String
  def applications:Future[List[Application]]
  def application(name:String):Future[Application]
}

trait Application {
  def name:String
  def clusters:Future[List[Cluster]]
}

trait Cluster {
  def name:String
  def scalingGroups:Future[List[ScalingGroup]]
  def loadBalancers:Future[List[LoadBalancer]]
}

trait ScalingGroup {
  def name:String
  def instances:Future[List[Instance]]
}

trait Instance {
  def name:String
  def hostname:Future[String]
  def privateAddresses:Future[List[InetAddress]]
  def publicAddresses:Future[List[InetAddress]]
  def loadBalancers:Future[List[LoadBalancer]]
  def loadBalancerState:Future[Map[String, LoadBalancerState.LoadBalancerState]]
}

trait LoadBalancer {
  def name:String
  def hostname:Future[String]
  def addresses:Future[List[InetAddress]]
  def instances:Future[List[Instance]]
  def status:Future[Map[String, LoadBalancerState.LoadBalancerState]]
}

object LoadBalancerState extends Enumeration {
  type LoadBalancerState = Value
  val InService, OutOfService, NoLoadBalancer = Value

  def apply(name:String): LoadBalancerState = {
    name match {
      case "InService" => InService
      case "OutOfService" => OutOfService
      case _ => NoLoadBalancer
    }
  }
}