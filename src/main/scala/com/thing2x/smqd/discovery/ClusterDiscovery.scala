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

package com.thing2x.smqd.discovery

import akka.actor.{ActorSystem, Address, AddressFromURIString}
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * 2018. 6. 28. - Created by Kwon, Yeong Eon
  */
trait ClusterDiscovery {
  def seeds: Future[Seq[akka.actor.Address]]
}

class StaticClusterDiscovery(config: Config)(implicit system: ActorSystem, ec: ExecutionContext) extends ClusterDiscovery {
  override def seeds: Future[Seq[Address]] = {
    Future {
      config.getStringList("seeds").asScala
        .map("akka.tcp://"+system.name+"@"+_)
        .map(AddressFromURIString.parse)
        .toList
    }
  }
}

class ManualClusterDiscovery(config: Config)(implicit system: ActorSystem, ec: ExecutionContext) extends ClusterDiscovery {
  override def seeds: Future[Seq[Address]] = ???
}

class FailedClusterDiscovery(config: Config)(implicit system: ActorSystem, ec: ExecutionContext) extends ClusterDiscovery {
  override def seeds: Future[Seq[Address]] = Future(Nil)
}
