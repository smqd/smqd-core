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

import java.net.URI

import com.thing2x.smqd.Smqd
import com.typesafe.config.Config

/**
  * 2018. 7. 5. - Created by Kwon, Yeong Eon
  */

class PluginPackageDefinition(val name: String, val vendor: String, val description: String,
                              val plugins: Seq[PluginDefinition],
                              val repository: PluginRepositoryDefinition)
  extends Ordered[PluginPackageDefinition] {

  override def equals(other: Any): Boolean = {
    if (!other.isInstanceOf[PluginPackageDefinition]) return false
    val r = other.asInstanceOf[PluginPackageDefinition]

    this.name == r.name
  }

  override def compare(that: PluginPackageDefinition): Int = this.name.compare(that.name)
}

class PluginDefinition(val name: String, val clazz: Class[SmqPlugin], val packageName: String, val version: String, val defaultConfig: Config, val multiInstantiable: Boolean)
  extends Ordered[PluginDefinition]{

  private var instances0: Seq[PluginInstance[SmqPlugin]] = Nil

  def instances: Seq[PluginInstance[SmqPlugin]] = instances0

  def createServiceInstance(instanceName: String, smqd: Smqd, config: Option[Config]): Service =
    createInstance(instanceName, smqd, config, classOf[Service])

  def createBridgeDriverInstance(instanceName: String, smqd: Smqd, config: Option[Config]): BridgeDriver =
    createInstance(instanceName, smqd, config, classOf[BridgeDriver])

  def createInstance[T <: SmqPlugin](instanceName: String, smqd: Smqd, config: Option[Config], pluginType: Class[T]): T = {
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

object PluginInstance {
  def apply[T <: SmqPlugin](instance: T, pluginDef: PluginDefinition) = new PluginInstance(instance, pluginDef)
}

class PluginInstance[+T <: SmqPlugin](val instance: T, val pluginDef: PluginDefinition) {
  val name: String = instance.name

  /** UNKNOWN, STOPPED, STOPPING, STARTING, RUNNING */
  def status: String = instance.status.toString

  /** packageName / pluginName / instanceName */
  val path: String = s"${pluginDef.packageName}/${pluginDef.name}/$name"
}

object PluginRepositoryDefinition {
  def apply(name: String, location: URI, provider: String, installable: Boolean) =
    new PluginRepositoryDefinition(name, location, provider, installable)
}

class PluginRepositoryDefinition(val name: String, val location: URI, val provider: String, val installable: Boolean) {
  private var installedPkg: Option[PluginPackageDefinition] = None
  def installed: Boolean = installedPkg.isDefined
  def packageDefinition: Option[PluginPackageDefinition] = installedPkg

  private[plugin] def setInstalledPackage(pkgDef: PluginPackageDefinition): Unit = {
    installedPkg = Option(pkgDef)
  }
}
