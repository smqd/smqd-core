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

package com.thing2x.smqd.replica

import akka.actor.{Actor, ActorRef, Stash}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.thing2x.smqd.replica.ReplicationActor._
import com.thing2x.smqd._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * 2018. 6. 15. - Created by Kwon, Yeong Eon
  */
object ReplicationActor {
  val actorName: String = "replicator"

  case class AddRoute(filter: FilterPath)
  case class RemoveRoute(filter: FilterPath)

  case class AddRetainedMessage(topicPath: TopicPath, msg: ByteBuf)
  case class RemoveRetainedMessage(topicPath: TopicPath)
}

class ClusterModeReplicationActor(router: Router, retainer: Retainer, nodeRouter: ActorRef) extends Actor with StrictLogging {
  private val replicator = DistributedData(context.system).replicator
  private implicit val node: Cluster = Cluster(context.system)

  private val FiltersKey = ORMultiMapKey[FilterPath, SmqdRoute]("smqd.filters")
  private val RetainsKey = LWWMapKey[TopicPath, ByteBuf]("smqd.retains")

  override def preStart(): Unit = {
    replicator ! Subscribe(FiltersKey, self)
    replicator ! Subscribe(RetainsKey, self)
  }

  override def postStop(): Unit = {
    replicator ! Unsubscribe(FiltersKey, self)
    replicator ! Unsubscribe(RetainsKey, self)
  }

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)

      sender ! ReadyAck
    case _ =>
      logger.warn("{} is not ready", self.path.name)
  }

  def receive0: Receive = {

    case AddRoute(filter) =>
      replicator ! Update(FiltersKey, ORMultiMap.empty[FilterPath, SmqdRoute], WriteMajority(1 second))(m => m.addBinding(filter, SmqdRoute(filter, nodeRouter)))

    case RemoveRoute(filter) =>
      replicator ! Update(FiltersKey, ORMultiMap.empty[FilterPath, SmqdRoute], WriteMajority(1 second))(m => m.removeBinding(filter, SmqdRoute(filter, nodeRouter)))

    case c @ Changed(FiltersKey) =>
      val data = c.get(FiltersKey)
      router.set(data.entries)

    case AddRetainedMessage(topic, msg) =>
      replicator ! Update(RetainsKey, LWWMap.empty[TopicPath, ByteBuf], WriteMajority(1 second))( _ + (topic, msg))

    case RemoveRetainedMessage(topic) =>
      replicator ! Update(RetainsKey, LWWMap.empty[TopicPath, ByteBuf], WriteMajority(1 second))( _ - topic)

    case c @ Changed(RetainsKey) =>
      val data = c.get(RetainsKey)
      retainer.set(data.entries)
  }
}

class LocalModeReplicationActor extends Actor with StrictLogging {

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)

      sender ! ReadyAck
    case _ =>
      logger.warn("{} is not ready", self.path.name)
  }

  def receive0: Receive = {
    case AddRetainedMessage(topic, msg) =>
    case RemoveRetainedMessage(topic) =>
  }
}
