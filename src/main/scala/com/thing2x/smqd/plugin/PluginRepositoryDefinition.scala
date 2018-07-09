package com.thing2x.smqd.plugin

import java.net.URI

import com.thing2x.smqd.plugin.PluginRepositoryDefinition.IvyModule
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

/**
  * 2018. 7. 8. - Created by Kwon, Yeong Eon
  */
object PluginRepositoryDefinition {
  def apply(name: String, provider: String, location: URI, installable: Boolean, description: String) =
    new PluginRepositoryDefinition(name, provider, Some(location), None, installable, description: String)
  def apply(name: String, provider: String, module: IvyModule, installable: Boolean, description: String) =
    new PluginRepositoryDefinition(name, provider, None, Some(module), installable, description)

  case class IvyModule(group: String, artifact: String, version: String, resolvers: Vector[String])
}

class PluginRepositoryDefinition(val name: String,
                                 val provider: String,
                                 val location: Option[URI],
                                 val module: Option[IvyModule],
                                 val installable: Boolean,
                                 val description: String)
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
        case _ =>
          ExecUnknownCommand(cmd)
      }
    }
    catch {
      case ex: Throwable => ExecFailure(s"Fail to $cmd package '$name'", Some(ex))
    }
  }
}
