// Copyright 2018 UANGEL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.thing2x.smqd.util

import akka.actor.{ActorSystem, Address, AddressFromURIString}
import akka.cluster.Cluster

import scala.concurrent.duration._

/**
  * 2018. 7. 1. - Created by Kwon, Yeong Eon
  */
trait AkkaSystemAware {
  def isClusterMode(implicit system: ActorSystem): Boolean = {
    system.settings.ProviderClass match {
      case "akka.cluster.ClusterActorRefProvider" => true
      case _ => false
    }
  }

  def cluster(implicit system: ActorSystem): Option[Cluster] = {
    if (isClusterMode)
      Some(Cluster(system))
    else None
  }

  def localNodeHostPort(nodeName: String)(implicit system: ActorSystem): String = {
    if (isClusterMode) {
      val name = system.settings.name
      val host = system.settings.config.getString("akka.remote.netty.tcp.hostname")
      val port = system.settings.config.getString("akka.remote.netty.tcp.port")
      s"$name@$host:$port"
    } else {
      nodeName
    }
  }

  def localNodeAddress(nodeName: String)(implicit system: ActorSystem): Address = {
    if (isClusterMode)
      AddressFromURIString.parse(s"akka.tcp://${localNodeHostPort(nodeName)}")
    else
      Address("", system.settings.name, nodeName, 0)
  }

  def uptime(implicit system: ActorSystem): Duration = {
    system.uptime.second // uptime  Start-up time since the epoch
  }

  def uptimeString(implicit system: ActorSystem): String = humanReadableTime(system.uptime * 1000)

}
