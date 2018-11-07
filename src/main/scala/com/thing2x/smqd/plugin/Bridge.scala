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

package com.thing2x.smqd.plugin

import akka.actor.ActorRef
import com.thing2x.smqd.{FilterPath, LifeCycle, TopicPath}
import com.typesafe.scalalogging.StrictLogging

// 2018. 7. 7. - Created by Kwon, Yeong Eon

trait Bridge extends LifeCycle with StrictLogging with Ordered[Bridge] {
  def filterPath: FilterPath
  def driver: BridgeDriver
  def index: Long

  override def compare(that: Bridge): Int = this.index.compareTo(that.index)

  override def toString: String = new StringBuilder("Bridge '")
    .append(driver.name).append("' ")
    .append("[").append(index).append("] ")
    .append(filterPath.toString)
    .toString
}

abstract class AbstractBridge(val driver: BridgeDriver, val index: Long, val filterPath: FilterPath)
  extends Bridge with StrictLogging {

  private var subr: Option[ActorRef] = None

  override def start(): Unit = {
    subr = Some(driver.smqdInstance.subscribe(filterPath, { (topicPath, msg) =>
      logger.trace(s"Bridge '${filterPath.toString}' bridge message: $msg")
      bridge(topicPath, msg)} ))
    logger.info(s"Bridge '${filterPath.toString}' started.")
  }

  override def stop(): Unit = {
    subr match {
      case Some(actor) =>
        driver.smqdInstance.unsubscribe(filterPath, actor)
      case _ =>
    }
    logger.info(s"Bridge '${filterPath.toString}' stopped.")
  }

  def bridge(topic: TopicPath, msg: Any): Unit
}
