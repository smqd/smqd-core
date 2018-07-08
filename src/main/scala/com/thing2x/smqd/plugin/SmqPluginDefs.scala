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

import java.io.File
import java.net.URI

import com.thing2x.smqd.Smqd
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import sbt.librarymanagement.UnresolvedWarning

import scala.concurrent.{ExecutionContext, Future}

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

object PluginInstance {
  def apply[T <: Plugin](instance: T, pluginDef: PluginDefinition) = new PluginInstance(instance, pluginDef)
}

class PluginInstance[+T <: Plugin](val instance: T, val pluginDef: PluginDefinition) {
  val name: String = instance.name

  /** UNKNOWN, STOPPED, STOPPING, STARTING, RUNNING */
  def status: String = instance.status.toString

  /** packageName / pluginName / instanceName */
  val path: String = s"${pluginDef.packageName}/${pluginDef.name}/$name"

  def exec(cmd: String)(implicit ec: ExecutionContext): Future[ExecResult] = Future {
    try {
      cmd match {
        case "start" =>
          instance.status match {
            case InstanceStatus.UNKNOWN | InstanceStatus.STOPPED =>
              instance.execStart()
              ExecSuccess(s"Instance '$name' is ${instance.status}")
            case status =>
              ExecInvalidStatus(s"Instance '$name' is $status")
          }
        case "stop" =>
          instance.status match {
            case InstanceStatus.UNKNOWN | InstanceStatus.RUNNING =>
              instance.execStop()
              ExecSuccess(s"Instance '$name' is ${instance.status}")
            case status =>
              ExecInvalidStatus(s"Instance '$name' is $status")
          }
        case _ =>
          ExecUnknownCommand(cmd)
      }
    }
    catch {
      case ex: Throwable => ExecFailure(s"Fail to $cmd instance '$name", Some(ex))
    }
  }
}

object PluginRepositoryDefinition {
  def apply(name: String, provider: String, location: URI, installable: Boolean) =
    new PluginRepositoryDefinition(name, provider, Some(location), None, installable)
  def apply(name: String, provider: String, group: String, artifact: String, version: String, installable: Boolean) =
    new PluginRepositoryDefinition(name, provider, None, Some((group, artifact, version)), installable)
}

class PluginRepositoryDefinition(val name: String, val provider: String, val location: Option[URI], val module: Option[(String, String, String)], val installable: Boolean)
  extends Ordered[PluginRepositoryDefinition] with StrictLogging {

  private var installedPkg: Option[PluginPackageDefinition] = None
  def installed: Boolean = installedPkg.isDefined
  def packageDefinition: Option[PluginPackageDefinition] = installedPkg

  val isIvyModule: Boolean = module.isDefined
  val isRemoteFile: Boolean = location.isDefined

  private[plugin] def setInstalledPackage(pkgDef: PluginPackageDefinition): Unit = {
    installedPkg = Option(pkgDef)
  }

  override def compare(that: PluginRepositoryDefinition): Int = this.name.compare(that.name)

  def exec(cmd: String, params: Map[String, Any])(implicit ec: ExecutionContext): Future[ExecResult] = Future {
    try {
      cmd match {
        case "install" =>
          val pluginDir = params.get("plugin.dir")

          if (!installable) {
            ExecInvalidStatus(s"Package '$name' is not installable")
          }
          else if (pluginDir.isEmpty) {
            ExecInvalidStatus(s"Plugin manager's root directory is not defined")
          }
          else if (isRemoteFile) {
            install_remote(location.get)
            ExecSuccess(s"Installing package '$name")
          }
          else if (isIvyModule) {
            install_ivy(module.get._1, module.get._2, module.get._3, pluginDir.get.asInstanceOf[File])
            ExecSuccess(s"Installing package '$name")
          }
          else {
            ExecInvalidStatus(s"Package '$name' has no valid repository information")
          }
        case _ =>
          ExecUnknownCommand(cmd)
      }
    }
    catch {
      case ex: Throwable => ExecFailure(s"Fail to $cmd package '$name'", Some(ex))
    }
  }

  private def install_remote(uri: URI): Unit = {

  }

  private def install_ivy(group: String, artifact: String, version: String, rootDir: File): Unit = {
    import sbt.librarymanagement.syntax._
    import sbt.librarymanagement.ivy._

    val fileRetrieve = new File(rootDir, "ivy")
    val fileCache = new File(rootDir, "ivy/cache")

    val ivyLogger = sbt.util.LogExchange.logger("com.thing2x.smqd.plugin")
    val ivyConfig = InlineIvyConfiguration().withLog(ivyLogger).withResolutionCacheDir(fileCache)
    val module = group %% artifact % version

    val lm = IvyDependencyResolution(ivyConfig)
    lm.retrieve(module, scalaModuleInfo = None, fileRetrieve, ivyLogger) match {
      case Left(w: UnresolvedWarning) =>
        val str= w.failedPaths.map(_.toString).mkString("\n", "\n", "\n")
        logger.warn(s"UnresolvedWarning -- $str", w.resolveException)
      case Right(files: Vector[File]) =>
        val str = files.map(_.getPath).mkString("\n", "\n", "\n")
        logger.trace(s"Resolved: $str")
    }

  }
}
