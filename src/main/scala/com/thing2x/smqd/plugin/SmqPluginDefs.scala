package com.thing2x.smqd.plugin

import java.net.URI

import com.typesafe.config.Config

/**
  * 2018. 7. 5. - Created by Kwon, Yeong Eon
  */

case class PluginPackageDefinition(name: String, vendor: String, description: String, plugins: Seq[PluginDefinition], repository: PluginRepositoryDefinition)

case class PluginDefinition(name: String, clazz: Class[SmqPlugin], version: String, config: Config, multiplicable: Boolean)

case class PluginRepositoryDefinition(name: String, location: URI, provider: String, installable: Boolean) {
  private var installedPkg: Option[PluginPackageDefinition] = None
  def installed: Boolean = installedPkg.isDefined
  def packageDefinition: Option[PluginPackageDefinition] = installedPkg

  private[plugin] def setInstalledPackage(pkgDef: PluginPackageDefinition): Unit = {
    installedPkg = Option(pkgDef)
  }
}
