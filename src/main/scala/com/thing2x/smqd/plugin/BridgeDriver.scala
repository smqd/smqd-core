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

import java.util.concurrent.atomic.AtomicLong

import com.thing2x.smqd.{FilterPath, LifeCycle, Smqd}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.collection.{SortedSet, mutable}

/**
  * 2018. 7. 7. - Created by Kwon, Yeong Eon
  */

abstract class BridgeDriver(val name: String, val smqd: Smqd, val config: Config) extends PluginLifeCycle with StrictLogging {
  private val indexes: AtomicLong = new AtomicLong()

  protected val bridgeSet: mutable.SortedSet[Bridge] = mutable.SortedSet.empty

  def bridges: SortedSet[Bridge] = bridgeSet

  def addBridge(filterPath: FilterPath, config: Config): Bridge = {
    val b = createBridge(filterPath, config)
    b.start()
    bridgeSet.add(b)
    b
  }

  def removeBridge(index: Long): Option[Bridge] = {
    bridge(index) match {
      case Some(b) =>
        bridgeSet.remove(b)
        b.stop()
        Some(b)
      case _ =>
        None
    }
  }

  def removeBridge(bridge: Bridge): Boolean = {
    bridgeSet.remove(bridge)
  }

  def removeAllBridges(): Unit = {
    bridgeSet.foreach(_.stop())
    bridgeSet.clear()
  }

  def bridge(index: Long): Option[Bridge] = bridgeSet.find(_.index == index)

  def startBridge(index: Long): Option[Bridge] = bridge(index) match {
    case Some(b) =>
      b.start()
      Some(b)
    case None =>
      None
  }

  def stopBridge(index: Long): Option[Bridge] = bridge(index) match {
    case Some(b) =>
      b.stop()
      Some(b)
    case None =>
      None
  }

  protected def createBridge(filterPath: FilterPath, config: Config, index: Long = indexes.getAndIncrement()): Bridge

  override def toString: String = new StringBuilder("BridgeDriver '")
    .append(name).append("' ")
    .append(" has ").append(bridgeSet.size).append(" bridge(s)")
    .toString

  /////////////////////////////////
  // LifeCycle
  private var _isClosed: Boolean = false
  val isClosed: Boolean = _isClosed

  override def start(): Unit = {
    logger.info(s"BridgeDriver '$name' starting...")
    connect()
    logger.info(s"BridgeDriver '$name' started.")
  }

  override def stop(): Unit = {
    logger.info(s"BridgeDrive '$name' stopping...")
    _isClosed = true
    removeAllBridges()
    disconnect()
    logger.info(s"BridgeDriver '$name' stopped.")
  }

  protected def connect(): Unit
  protected def disconnect(): Unit

}
