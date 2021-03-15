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

import java.net.URI

import akka.actor.{Actor, ActorSystem, Address, AddressFromURIString, Cancellable, Props}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import mousio.etcd4j.EtcdClient

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/** 2018. 6. 28. - Created by Kwon, Yeong Eon
  */
class EtcdClusterDiscovery(config: Config, nodeName: String, selfAddress: Address)(implicit system: ActorSystem, ec: ExecutionContext) extends ClusterDiscovery with StrictLogging {

  private val server = config.getString("server")
  private val prefix = config.getString("prefix")
  private val ttl = config.getDuration("node_ttl").toMillis.millis

  override def seeds: Future[Seq[Address]] = {
    val rt = Promise[Seq[Address]]()
    Future {
      logger.info("etcd connecting to {}...", server)
      val etcd = new EtcdClient(URI.create(server))

      val dir = if (prefix.endsWith("/")) prefix else prefix + "/"
      val key = dir + nodeName
      val value = selfAddress.toString

      // register my address as seed
      val resp = etcd.put(key, value).ttl((ttl.toMillis / 1000).toInt).send()
      logger.info("etcd write: {} = {}", key, resp.get.node.value)

      // retrieve all seed nodes addresses
      val lst = etcd.getDir(dir).sorted().recursive().send().get()
      val m = lst.node.nodes.asScala.map(n => n.key.substring(dir.length) -> n.value)
      logger.info("etcd  read: {}", m.map(n => s"${n._1} = ${n._2}").mkString(", "))

      val result = m.map { case (node, addr) =>
        logger.info(s"etcd  seed: $node = ${addr.toString}")
        AddressFromURIString.parse(addr)
      }

      rt.success(result.toSeq)

      // only after we returns seed nodes successfully, the actor system can be initialized.
      // then RefreshActor can be instantiated. so only at this point we can create an actor
      val timer = new java.util.Timer()
      val refreshRate = 0.75f
      val task = new java.util.TimerTask {
        override def run(): Unit = {
          //system.actorSelection("/user/chief") ! Props(classOf[RefreshActor], etcd, key, ttl)
          system.actorOf(Props(classOf[RefreshActor], etcd, key, ttl, refreshRate), "EtcdClsuterDiscovery")
        }
      }
      val timerStartAfterMillis = (ttl.toMillis * 0.3 * (1 - refreshRate)).toLong
      timer.schedule(task, timerStartAfterMillis)
    }

    rt.future
  }
}

object RefreshActor {
  case object Tick
}

class RefreshActor(etcd: EtcdClient, key: String, ttl: FiniteDuration, refreshRate: Float = 0.75f) extends Actor with StrictLogging {

  private var cancel: Cancellable = _

  override def preStart(): Unit = {
    val period = (ttl.toMillis * 0.75).toInt.millis
    implicit val ec: ExecutionContext = context.system.dispatchers.defaultGlobalDispatcher
    cancel = context.system.scheduler.scheduleAtFixedRate(period, period, self, RefreshActor.Tick)
    logger.trace("etcd refresh timer scheduled: {} seconds", period.toSeconds)
  }

  override def postStop(): Unit = {
    cancel.cancel()
    // delete myself from etcd before shutdown
    etcd.delete(key).send()
    logger.trace("etcd deleted: {}", key)
  }

  override def receive: Receive = { case RefreshActor.Tick =>
    etcd.refresh(key, (ttl.toMillis / 1000).toInt).send()
    logger.trace("etcd refreshed: {}", key)
  }
}
