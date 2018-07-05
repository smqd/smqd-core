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

case class PluginPackageDefinition(name: String, vendor: String, description: String, plugins: Seq[PluginDefinition], repository: PluginRepositoryDefinition)

case class PluginDefinition(name: String, clazz: Class[SmqPlugin], version: String, defaultConfig: Config, multiplicable: Boolean) {

  def createServiceInstance(instanceName: String, smqd: Smqd, config: Config): SmqServicePlugin =
    createInstance(instanceName, smqd, config, classOf[SmqServicePlugin])

  def createInstance[T <: SmqPlugin](instanceName: String, smqd: Smqd, config: Config, pluginType: Class[T]): T = {
    val clazz = this.clazz.asInstanceOf[Class[T]]
    val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config])
    val mergedConf = config.withFallback(defaultConfig)
    val instance = cons.newInstance(instanceName, smqd, mergedConf)
    instance
  }
}

case class PluginRepositoryDefinition(name: String, location: URI, provider: String, installable: Boolean) {
  private var installedPkg: Option[PluginPackageDefinition] = None
  def installed: Boolean = installedPkg.isDefined
  def packageDefinition: Option[PluginPackageDefinition] = installedPkg

  private[plugin] def setInstalledPackage(pkgDef: PluginPackageDefinition): Unit = {
    installedPkg = Option(pkgDef)
  }
}
