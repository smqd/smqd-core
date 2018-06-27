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

package t2x.smqd

import akka.actor.{ActorRef, ActorSystem}
import io.netty.buffer.{ByteBuf, ByteBufUtil}
import t2x.smqd.QoS.QoS
import t2x.smqd.replica.ReplicationActor
import t2x.smqd.util.ActorIdentifying

import scala.collection.mutable

/**
  * 2018. 6. 15. - Created by Kwon, Yeong Eon
  */
trait Retainer {

  def set(map: Map[TopicPath, ByteBuf]): Unit

  def put(topicPath: TopicPath, msg: ByteBuf): Unit

  def remove(topicPath: TopicPath): Unit

  def filter(filterPath: FilterPath, qos: QoS): Seq[RetainedMessage]
}

case class RetainedMessage(topicPath: TopicPath, qos: QoS, msg: ByteBuf)

class ClusterModeRetainer(system: ActorSystem) extends Retainer with ActorIdentifying {

  private lazy val ddManager: ActorRef = identifyActor(manager(ReplicationActor.actorName))(system)

  private var maps: Map[TopicPath, ByteBuf] = Map.empty

  def set(maps: Map[TopicPath, ByteBuf]): Unit = {
    this.maps = maps

    logger.trace(maps.map{ case (k, v) => s"${k.toString}\n${ByteBufUtil.prettyHexDump(v)}"}.mkString("\nSet retained messages\n   \t", "\n   \t", ""))
  }

  override def put(topicPath: TopicPath, msg: ByteBuf): Unit = {
    ddManager ! ReplicationActor.AddRetainedMessage(topicPath, msg)
  }

  override def remove(topicPath: TopicPath): Unit = {
    ddManager ! ReplicationActor.RemoveRetainedMessage(topicPath)
  }

  override def filter(filterPath: FilterPath, qos: QoS): Seq[RetainedMessage] = {
    maps.filter{ case (k, _) => filterPath.matchFor(k) }.map{ case (topic, msg) => RetainedMessage(topic, qos, msg)}.toList
  }
}

class LocalModeRetainer extends Retainer {

  private val maps: mutable.Map[TopicPath, ByteBuf] = mutable.HashMap.empty

  override def set(map: Map[TopicPath, ByteBuf]): Unit = ???

  override def put(topicPath: TopicPath, msg: ByteBuf): Unit = {
    maps.put(topicPath, msg)
  }

  override def remove(topicPath: TopicPath): Unit = {
    maps.remove(topicPath)
  }

  override def filter(filterPath: FilterPath, qos: QoS): Seq[RetainedMessage] = {
    maps.filter{ case (k, _) => filterPath.matchFor(k) }.map{ case (topic, msg) => RetainedMessage(topic, qos, msg)}.toList
  }
}
