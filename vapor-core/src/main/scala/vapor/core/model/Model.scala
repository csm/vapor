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

trait Environment {
  def name:String
  def dataCenters:List[DataCenter]
}

trait DataCenter {
  def name:String
  def applications:List[Application]
  def application(name:String):Option[Application]
}

trait Application {
  def name:String
  def clusters:List[Cluster]
}

trait Cluster {
  def name:String
  def scalingGroups:List[ScalingGroup]
}

trait ScalingGroup {
  def name:String
  def instances:List[Instance]
}

trait Instance {
  def id:String
  def hostname:String
  def privateAddresses:List[InetAddress]
  def publicAddresses:List[InetAddress]
}

trait LoadBalancer {
  def id:String
  def hostname:String
  def addresses:List[InetAddress]
}