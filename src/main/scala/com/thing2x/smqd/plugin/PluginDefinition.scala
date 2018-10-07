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
import com.thing2x.smqd.plugin.PluginManager.CORE_PKG
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable

// 2018. 7. 5. - Created by Kwon, Yeong Eon

object PluginDefinition{
  def apply(name: String, clazz: Class[Plugin], packageName: String, version: String, multiInstantiable: Boolean, defaultConfig: Config, configSchema: Config) =
    new PluginDefinition(name, clazz, packageName, version, multiInstantiable, defaultConfig, configSchema)

  /** create a PluginDefinition for a class based plugin implementation which is using "entry.class" to be wired with smqd */
  def nonPluggablePlugin(name: String, clazz: Class[Plugin]): PluginDefinition = {
    PluginDefinition(name, clazz, PluginManager.POJO_PKG, "n/a", multiInstantiable = false, ConfigFactory.empty, ConfigFactory.empty)
  }
}

class PluginDefinition(val name: String, val clazz: Class[Plugin], val packageName: String, val version: String, val multiInstantiable: Boolean, val defaultConfig: Config, val configSchema: Config)
  extends Ordered[PluginDefinition]{

  private var instances0: mutable.SortedSet[InstanceDefinition[Plugin]] = mutable.SortedSet.empty

  def instances: mutable.SortedSet[InstanceDefinition[Plugin]] = instances0

  def createInstance[T <: Plugin](instanceName: String, smqd: Smqd, config: Option[Config], autoStart: Boolean): InstanceDefinition[T] = {
    val clazz = this.clazz.asInstanceOf[Class[T]]
    val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config])
    val mergedConf = if (config.isDefined) config.get.withFallback(defaultConfig) else defaultConfig
    val instance = cons.newInstance(instanceName, smqd, mergedConf)
    val pluginInstance = InstanceDefinition(instance, this, autoStart)
    this.instances0 += pluginInstance
    pluginInstance
  }

  def removeInstance(instanceName: String): Boolean = {
    this.instances0.find(_.name == instanceName) match {
      case Some(instDef) =>
        this.instances0 = instances0.filter(_ != instDef)
        true
      case None =>
        false
    }
  }

  override def equals(other: Any): Boolean = {
    if (!other.isInstanceOf[PluginDefinition]) return false
    val r = other.asInstanceOf[PluginDefinition]

    this.packageName == r.packageName && this.name == r.name
  }

  override def compare(that: PluginDefinition): Int = {
    // make smqd-core package come first
    val delta = (this.packageName, that.packageName) match {
      case (PluginManager.CORE_PKG, _) => -1
      case (_, PluginManager.CORE_PKG) => 1
      case (l, r) if l.startsWith("smqd-") && r.startsWith("smqd-") => l.compare(r)
      case (l, r) if l.startsWith("smqd-") && !r.startsWith("smqd-") => -1
      case (l, r) if !l.startsWith("smqd-") && r.startsWith("smqd-") => 1
      case _ => this.packageName.compare(that.packageName)
    }

    if (delta == 0) this.name.compare(that.name) else delta
  }
}
