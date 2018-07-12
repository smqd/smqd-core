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

package com.thing2x.smqd.rest.api

import java.io.File

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd._
import com.thing2x.smqd.plugin.PluginManager._
import com.thing2x.smqd.plugin._
import com.thing2x.smqd.rest.RestController
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import spray.json._

// 2018. 7. 6. - Created by Kwon, Yeong Eon

class PluginController(name: String, smqdInstance: Smqd, config: Config) extends RestController(name, smqdInstance, config) with Directives with StrictLogging {

  override def routes: Route = packages ~ plugins

  private def packages: Route = {
    ignoreTrailingSlash {
      get {
        parameters('curr_page.as[Int].?, 'page_size.as[Int].?, 'query.as[String].?) { (currPage, pageSize, searchName) =>
          path("packages" / Segment.?) { packageName =>
            getPackages(packageName, searchName, currPage, pageSize)
          }
        }
      } ~
      put {
        path("packages" / Segment / Segment) { (packageName, cmd) =>
          putPackage(packageName, cmd)
        }
      }
    }
  }

  private def plugins: Route = {
    ignoreTrailingSlash {
      get {
        parameters('curr_page.as[Int].?, 'page_size.as[Int].?, 'query.as[String].?) { (currPage, pageSize, searchName) =>
          path("plugins" / Segment / "config") { pluginName =>
            getPluginConfig(pluginName)
          } ~
          path("plugins" / Segment / "instances" / Segment / "config") { (pluginName, instanceName) =>
            getPluginInstanceConfig(pluginName, instanceName)
          } ~
          path("plugins" / Segment / "instances" / Segment.?) { (pluginName, instanceName) =>
            getPluginInstances(pluginName, instanceName, searchName, currPage, pageSize)
          } ~
          path("plugins" / Segment.?) { pluginName =>
            getPlugins(pluginName, searchName, currPage, pageSize)
          }
        }
      } ~
      put {
        path("plugins" / Segment / "instances" / Segment / Segment) { (pluginName, instanceName, cmd) =>
          putPlugin(pluginName, instanceName, cmd)
        }
      } ~
      path("plugins" / Segment / "instances" / Segment) { (pluginName, instanceName) =>
        post {
          entity(as[com.typesafe.config.Config]) { conf =>
            createPluginInstance(pluginName, instanceName, conf)
          }
        } ~
        patch {
          entity(as[com.typesafe.config.Config]) { conf =>
            updatePluginInstance(pluginName, instanceName, conf)
          }
        } ~
        delete {
          deletePluginInstance(pluginName, instanceName)
        }
      }
    }
  }

  private def getPackages(packageName: Option[String], searchName: Option[String], currPage: Option[Int], pageSize: Option[Int]): Route = {
    val pm = smqdInstance.pluginManager
    packageName match {
      case Some(pn) => // exact match
        val rt = pm.packageDefinitions
        rt.find(_.name == pn) match {
          case Some(pkg) =>
            complete(StatusCodes.OK, restSuccess(0, PluginPackageDefinitionFormat.write(pkg)))
          case None =>
            complete(StatusCodes.NotFound, restError(404, s"Package not found: $pn"))
        }
      case None => // search
        searchName match {
          case Some(search) => // query
            val rt = pm.packageDefinitions
            val result = rt.filter(p => p.name.contains(search)).sorted
            if (result.isEmpty)
              complete(StatusCodes.NotFound, restError(404, s"Package not found, search $search"))
            else
              complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
          case None => // all - retrieve repository definitions instead of package defs.
            val result = pm.repositoryDefinitions.sorted
            complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
        }
    }
  }

  private def execResult(result: ExecResult): JsValue = {
    result match {
      case ExecSuccess(msg) =>
        restSuccess(0, JsString(msg))
      case ExecFailure(message, Some(cause)) =>
        restError(StatusCodes.InternalServerError.intValue, s"Command failed - $message, ${cause.getMessage}")
      case ExecFailure(message, None) =>
        restError(StatusCodes.InternalServerError.intValue, message)
      case ExecInvalidStatus(message) =>
        restError(StatusCodes.NotImplemented.intValue, message)
      case ExecUnknownCommand(cmd) =>
        restError(StatusCodes.BadRequest.intValue, s"Not implemented command - $cmd")
      case x =>
        restError(StatusCodes.InternalServerError.intValue, s"Unknown response from plugin: $x")
    }
  }

  private def putPackage(packageName: String, cmd: String): Route = {
    val pm = smqdInstance.pluginManager
    pm.repositoryDefinition(packageName) match {
      case Some(rdef) =>
        import smqdInstance.Implicit._
        cmd.toLowerCase match {
          case "install" =>
            val jval = for {
              rt <- pm.installPackage(smqdInstance, rdef)
              result = rt match {
                case _: InstallSuccess => restSuccess(0, PluginRepositoryDefinitionFormat.write(rdef))
                case e: InstallResult => restError(500, e.msg)
              }
            } yield result
            complete(StatusCodes.OK, jval)
          case "reload" =>
            val jval = for {
              rt <- pm.reloadPackage(smqdInstance, rdef)
              result = rt match {
                case _: ReloadSuccess => restSuccess(0, PluginRepositoryDefinitionFormat.write(rdef))
                case e: ReloadResult => restError(500, e.msg)
              }
            } yield result
            complete(StatusCodes.OK, jval)
          case command =>
            val params: Map[String, Any] =  if (pm.libDirectory.isDefined) Map("plugin.dir" -> pm.libDirectory.get) else Map.empty
            val result = rdef.exec(command, params) map {
              case ExecSuccess(_) => restSuccess(0, PluginRepositoryDefinitionFormat.write(rdef))
              case rt => execResult(rt)
            }
            complete(StatusCodes.OK, result)
        }
      case None =>
        complete(StatusCodes.NotFound, s"Package not found :$packageName")
    }
  }

  private def putPlugin(pluginName: String, instanceName: String, command: String): Route = {
    val pm = smqdInstance.pluginManager
    val instanceOpt = pm.instance(pluginName, instanceName)
    instanceOpt match {
      case Some(instance) =>
        import smqdInstance.Implicit._
        val result = instance.exec(command.toLowerCase) map {
          case ExecSuccess(_) => restSuccess(0, PluginInstanceFormat.write(instance))
          case rt => execResult(rt)
        }
        complete(StatusCodes.OK, result)
      case None =>
        complete(StatusCodes.NotFound, restError(404, s"Plugin instance not found - $pluginName, $instanceName"))
    }
  }

  private def getPlugins(pluginName: Option[String], searchName: Option[String], currPage: Option[Int], pageSize: Option[Int]): Route = {
    val pm = smqdInstance.pluginManager
    pluginName match {
      case Some(pname) => // exact match
        pm.pluginDefinition(pname) match {
          case Some(p) =>
            complete(StatusCodes.OK, restSuccess(0, PluginDefinitionFormat.write(p)))
          case None =>
            complete(StatusCodes.NotFound, s"Plugin not found plugin: $pname")
        }
      case None => // search
        searchName match {
          case Some(search) => // query
            val result = pm.pluginDefinitions(search).sorted
            if (result.isEmpty)
              complete(StatusCodes.NotFound, restError(404, s"Plugin not found, search $search"))
            else
              complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
          case None => // all
            val result = pm.pluginDefinitions.sorted
            complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
        }
    }
  }

  private def getPluginInstances(pluginName: String, instanceName: Option[String], searchName: Option[String], currPage: Option[Int], pageSize: Option[Int]): Route = {
    val pm = smqdInstance.pluginManager
    instanceName match {
      case Some(instName) => // exact match
        pm.instance(pluginName, instName) match {
          case Some(inst) =>
            complete(StatusCodes.OK, restSuccess(0, PluginInstanceFormat.write(inst)))
          case None =>
            complete(StatusCodes.NotFound, s"Plugin instance not found plugin: $pluginName, instance: $instName")
        }
      case None => // search
        searchName match {
          case Some(search) => // query
            val result = pm.instances(pluginName, search).sorted
            if (result.isEmpty)
              complete(StatusCodes.NotFound, s"Plugin instance not found plugin: $pluginName, search $search")
            else
              complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
          case None => // all
            val result = pm.instances(pluginName).sorted
            complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
        }
    }
  }

  private def getPluginConfig(pluginName: String): Route = {
    val pm = smqdInstance.pluginManager
    pm.pluginDefinition(pluginName) match {
      case Some(pdef) =>
        val result = JsObject(
          "default-config" -> pdef.defaultConfig.toJson,
          "config-schema" -> pdef.configSchema.toJson
        )
        complete(StatusCodes.OK, restSuccess(0, result))
      case None =>
        complete(StatusCodes.NotFound, restError(404, s"Plugine not found $pluginName"))
    }
  }

  private def getPluginInstanceConfig(pluginName:String, instanceName: String): Route = {
    val pm = smqdInstance.pluginManager
    pm.instance(pluginName, instanceName) match {
      case Some(inst) =>
        val autoStart = inst.autoStart
        inst.instance match {
          case ap: AbstractPlugin =>
            complete(StatusCodes.OK, restSuccess(0, JsObject(
              "auto-start" -> JsBoolean(autoStart),
              "config" -> ap.config.toJson
            )))
          case _ =>
            complete(StatusCodes.OK, restSuccess(0, JsObject(
              "auto-start" -> JsBoolean(autoStart),
              "config" -> JsObject()
            )))
            //complete(StatusCodes.NotAcceptable, s"Plugin instance is not a configurable")
        }

      case None =>
        complete(StatusCodes.NotFound, s"Plugin instance not found plugin: $pluginName, instance: $instanceName")
    }
  }

  private def createPluginInstance(pluginName: String, instanceName: String, conf: Config): Route = {
    smqdInstance.pluginManager.configDirectory match {
      case Some(confDir) =>
        val file = new File(confDir, s"$pluginName-$instanceName.conf")
        if (file.exists()) {
          complete(StatusCodes.PreconditionFailed, restError(412, "same plugin and instance name already exists"))
        }
        else if (!file.getParentFile.canRead || !file.getParentFile.canWrite) {
          complete(StatusCodes.PreconditionFailed, restError(412, s"access denied. can't write the instance '$pluginName-$instanceName'"))
        }
        else {
          smqdInstance.pluginManager.createInstanceConfigFile(smqdInstance, pluginName, instanceName, file, conf) match {
            case Some(_) =>
              getPluginInstanceConfig(pluginName, instanceName)
            case None =>
              complete(StatusCodes.InternalServerError, restError(500, s"Unable to load plugin '$pluginName-$instanceName'"))
          }
        }

      case None =>
        complete(StatusCodes.InternalServerError, restError(500, s"plugin configuration directory is not set"))
    }
  }

  private def updatePluginInstance(pluginName: String, instanceName: String, conf: Config): Route = {
    val pm = smqdInstance.pluginManager
    pm.configDirectory match {
      case Some(confDir) =>
        val file = new File(confDir, s"$pluginName-$instanceName.conf")

        (file.exists, pm.instance(pluginName, instanceName)) match {
          case (true, Some(instDef)) =>
            instDef.instance.status match {
              case InstanceStatus.RUNNING | InstanceStatus.STARTING | InstanceStatus.STOPPING =>
                complete(StatusCodes.PreconditionFailed, restError(412, s"plugin instance is still running"))
              case InstanceStatus.STOPPED | InstanceStatus.UNKNOWN =>
                if (pm.updateInstanceConfigFile(smqdInstance, pluginName, instanceName, file, conf)) {
                  getPluginInstanceConfig(pluginName, instanceName)
                }
                else {
                  complete(StatusCodes.InternalServerError, restError(500, s"Fail to delete instance '$pluginName' '$instanceName'"))
                }
            }
          case _ =>
            complete(StatusCodes.NotFound, restError(404, s"Not found instance or configuration '$pluginName' '$instanceName'"))
        }

      case None =>
        complete(StatusCodes.NotFound, restError(404, s"Not found instance configuration '$pluginName' '$instanceName'"))
    }
  }

  private def deletePluginInstance(pluginName: String, instanceName: String): Route = {
    val pm = smqdInstance.pluginManager
    pm.configDirectory match {
      case Some(confDir) =>
        val file = new File(confDir, s"$pluginName-$instanceName.conf")

        (file.exists, pm.instance(pluginName, instanceName)) match {
          case (true, Some(instDef)) =>
            instDef.instance.status match {
              case InstanceStatus.RUNNING | InstanceStatus.STARTING | InstanceStatus.STOPPING =>
                complete(StatusCodes.PreconditionFailed, restError(412, s"plugin instance is still running"))
              case InstanceStatus.STOPPED | InstanceStatus.UNKNOWN =>
                if (pm.deleteInstanceConfigFile(smqdInstance, pluginName, instanceName, file))
                  complete(StatusCodes.OK, restSuccess(0, JsObject("success" -> JsString("plugin instance deleted"))))
                else
                  complete(StatusCodes.InternalServerError, restError(500, s"Fail to delete instance '$pluginName' '$instanceName'"))
            }
          case _ =>
            complete(StatusCodes.NotFound, restError(404, s"Not found instance or configuration '$pluginName' '$instanceName'"))
        }
      case None =>
        complete(StatusCodes.NotFound, restError(404, s"Not found instance configuration '$pluginName' '$instanceName'"))
    }
  }
}
