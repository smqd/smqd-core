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

import com.thing2x.smqd.plugin.RepositoryDefinition.MavenModule
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

// 2018. 7. 8. - Created by Kwon, Yeong Eon

object RepositoryDefinition {
  def apply(name: String, provider: String, location: URI, installable: Boolean, description: String) =
    new RepositoryDefinition(name, provider, Some(location), None, installable, description: String)
  def apply(name: String, provider: String, module: MavenModule, installable: Boolean, description: String) =
    new RepositoryDefinition(name, provider, None, Some(module), installable, description)

  case class MavenModule(group: String, artifact: String, version: String, resolvers: Vector[String])
}

class RepositoryDefinition(val name: String,
                           val provider: String,
                           val location: Option[URI],
                           val module: Option[MavenModule],
                           val installable: Boolean,
                           val description: String)
  extends Ordered[RepositoryDefinition] with StrictLogging {

  private var installedPkg: Seq[PackageDefinition] = Seq.empty
  def installed: Boolean = installedPkg.nonEmpty
  def packageDefinitions: Seq[PackageDefinition] = installedPkg

  val isMavenModule: Boolean = module.isDefined
  val isRemoteFile: Boolean = location.isDefined

  private[plugin] def setInstalledPackage(pkgDef: PackageDefinition): Unit = {
    installedPkg :+= pkgDef
  }

  def exec(cmd: String, params: Map[String, Any])(implicit ec: ExecutionContext): Future[ExecResult] = Future {
    try {
      cmd match {
        case _ =>
          ExecUnknownCommand(cmd)
      }
    }
    catch {
      case ex: Throwable => ExecFailure(s"Fail to $cmd package '$name'", Some(ex))
    }
  }

  override def compare(that: RepositoryDefinition): Int = {
    (this.name, that.name) match {
      case (PluginManager.CORE_PKG, _) => -1
      case (_, PluginManager.CORE_PKG) => 1
      case (l, r) if l.startsWith("smqd-") && r.startsWith("smqd-") => l.compare(r)
      case (l, r) if l.startsWith("smqd-") && !r.startsWith("smqd-") => -1
      case (l, r) if !l.startsWith("smqd-") && r.startsWith("smqd-") => 1
      case _ => this.name.compare(that.name)
    }
  }

}
