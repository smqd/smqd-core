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

package com.thing2x.smqd

import akka.actor.Actor
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, LWWMap, LWWMapKey, SelfUniqueAddress}
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.thing2x.smqd.QoS.QoS
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBufUtil

import scala.collection.mutable
import scala.concurrent.duration._

// 2018. 6. 15. - Created by Kwon, Yeong Eon

trait Retainer {

  def set(map: Map[TopicPath, Array[Byte]]): Unit

  def put(topicPath: TopicPath, msg: Array[Byte]): Unit

  def remove(topicPath: TopicPath): Unit

  def filter(filterPath: FilterPath, qos: QoS): Seq[RetainedMessage]
}

case class RetainedMessage(topicPath: TopicPath, qos: QoS, msg: Array[Byte])

class LocalModeRetainer extends Retainer {

  private val maps: mutable.Map[TopicPath, Array[Byte]] = mutable.HashMap.empty

  override def set(map: Map[TopicPath, Array[Byte]]): Unit = ???

  override def put(topicPath: TopicPath, msg: Array[Byte]): Unit =
    maps.put(topicPath, msg)

  override def remove(topicPath: TopicPath): Unit =
    maps.remove(topicPath)

  override def filter(filterPath: FilterPath, qos: QoS): Seq[RetainedMessage] = {
    maps.filter{ case (k, _) => filterPath.matchFor(k) }.map{ case (topic, msg) => RetainedMessage(topic, qos, msg)}.toList
  }
}

class ClusterModeRetainer extends Retainer with StrictLogging {

  private var maps: Map[TopicPath, Array[Byte]] = Map.empty

  def set(maps: Map[TopicPath, Array[Byte]]): Unit = {
    this.maps = maps

    logger.trace(maps.map{ case (k, v) => s"${k.toString}\n${ByteBufUtil.hexDump(v)}"}.mkString("\nSet retained messages\n   \t", "\n   \t", ""))
  }

  override def put(topicPath: TopicPath, msg: Array[Byte]): Unit = {
    replicator.addRetainedMessage(topicPath, msg)
  }

  override def remove(topicPath: TopicPath): Unit = {
    replicator.removeRetainedMessage(topicPath)
  }

  override def filter(filterPath: FilterPath, qos: QoS): Seq[RetainedMessage] = {
    maps.filter{ case (k, _) => filterPath.matchFor(k) }.map{ case (topic, msg) => RetainedMessage(topic, qos, msg)}.toList
  }

  private var replicator: RetainsReplicator = _
  private[smqd] def setReplicator(repl: RetainsReplicator): Unit = {
    replicator = repl
  }
}

object RetainsReplicator {
  val actorName: String = "retain_replicator"
}

class RetainsReplicator(smqd: Smqd, retainer: ClusterModeRetainer) extends Actor with StrictLogging {
  private implicit val node: SelfUniqueAddress = SelfUniqueAddress(Cluster(context.system).selfUniqueAddress)
  private val replicator = DistributedData(context.system).replicator
  private val RetainsKey = LWWMapKey[TopicPath, Array[Byte]]("smqd.retains")

  private val writeConsistency = WriteMajority(1.second)
  private val readConsistency = ReadMajority(1.second)

  override def preStart(): Unit = {
    replicator ! Subscribe(RetainsKey, self)
  }

  override def postStop(): Unit = {
    replicator ! Unsubscribe(RetainsKey, self)
  }

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      retainer.setReplicator(this)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case c @ Changed(RetainsKey) =>
      val data = c.get(RetainsKey)
      retainer.set(data.entries)
  }

  private[smqd] def addRetainedMessage(topic: TopicPath, msg: Array[Byte]): Unit = {
    replicator ! Update(RetainsKey, LWWMap.empty[TopicPath, Array[Byte]], writeConsistency)( _ :+ (topic, msg))
  }

  private[smqd] def removeRetainedMessage(topic: TopicPath): Unit = {
    replicator ! Update(RetainsKey, LWWMap.empty[TopicPath, Array[Byte]], writeConsistency)( _.remove(node, topic))
  }
}