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

import com.thing2x.smqd.Smqd
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

/**
  * 2018. 7. 5. - Created by Kwon, Yeong Eon
  */

class PluginDefinition(val name: String, val clazz: Class[Plugin], val packageName: String, val version: String, val defaultConfig: Config, val multiInstantiable: Boolean)
  extends Ordered[PluginDefinition]{

  private var instances0: Seq[PluginInstance[Plugin]] = Nil

  def instances: Seq[PluginInstance[Plugin]] = instances0

  def createServiceInstance(instanceName: String, smqd: Smqd, config: Option[Config]): Service =
    createInstance(instanceName, smqd, config, classOf[Service])

  def createBridgeDriverInstance(instanceName: String, smqd: Smqd, config: Option[Config]): BridgeDriver =
    createInstance(instanceName, smqd, config, classOf[BridgeDriver])

  def createInstance[T <: Plugin](instanceName: String, smqd: Smqd, config: Option[Config], pluginType: Class[T]): T = {
    val clazz = this.clazz.asInstanceOf[Class[T]]
    val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config])
    val mergedConf = if (config.isDefined) config.get.withFallback(defaultConfig) else defaultConfig
    val instance = cons.newInstance(instanceName, smqd, mergedConf)
    val pluginInstance = PluginInstance(instance, this)
    this.instances0 = instances0 :+ pluginInstance
    instance
  }

  override def equals(other: Any): Boolean = {
    if (!other.isInstanceOf[PluginDefinition]) return false
    val r = other.asInstanceOf[PluginDefinition]

    this.name == r.name
  }

  override def compare(that: PluginDefinition): Int = this.name.compare(that.name)
}
