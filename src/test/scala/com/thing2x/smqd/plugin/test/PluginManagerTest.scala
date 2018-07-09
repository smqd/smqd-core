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

package com.thing2x.smqd.plugin.test

import com.thing2x.smqd.net.http.HttpService
import com.thing2x.smqd.net.mqtt.MqttService
import com.thing2x.smqd.plugin.PluginManager
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.FlatSpec

import scala.sys.process._

/**
  * 2018. 7. 4. - Created by Kwon, Yeong Eon
  */
class PluginManagerTest extends FlatSpec with StrictLogging {

  "PluginManager" should "initialize - empty" in {
    var mgr = PluginManager("", "")
    val repos = mgr.repositoryDefinitions
    repos.foreach { repo =>
      val inst = if (repo.installed) "installed" else if (repo.installable) "installable" else "not installable"
      logger.info(s"1> Repo '${repo.name}' is $inst")

      val pkgs = repo.packageDefinition
      pkgs.foreach { pkg =>
        logger.info(s"1>      plugins: ${pkg.plugins.map(p => p.name).mkString(", ")}")
      }
    }
  }

  private val pwd = "pwd".!!.trim
  private val codebase = getClass.getProtectionDomain.getCodeSource.getLocation.getPath
  logger.debug(s"PWD: $pwd, codebase: $codebase")

  val mgr = PluginManager( codebase, "", s"file://$pwd/src/test/conf/smqd-plugins-manifest-custom.conf", "")

  "PluginManager" should "initialize" in {
    val repos = mgr.repositoryDefinitions
    repos.foreach { repo =>
      val inst = if (repo.installed) "installed" else if (repo.installable) "installable" else "not installable"
      logger.info(s"2> Repo '${repo.name}' is $inst")

      val pkgs = repo.packageDefinition
      pkgs.foreach { pkg =>
        logger.info(s"2>      plugins: ${pkg.plugins.map(p => p.name).mkString(", ")}")
      }
    }
  }


  it should "filter a package by name" in {

    // check repository def
    val rpopt = mgr.repositoryDefinition("Fake plugins")
    assert(rpopt.isDefined)
    val rp = rpopt.get

    assert(rp.name == "Fake plugins")
    assert(rp.location.isDefined)
    assert(rp.location.get.toString == "https://fake.com/smqd-fake_2.12.0.1.0.jar")
    assert(rp.provider == "www.smqd-test.com")

    val coreopt = mgr.repositoryDefinition("smqd-core")
    assert(coreopt.isDefined)
    val core = coreopt.get
    assert(core.name == "smqd-core")
    assert(core.installed)

    val pdefopt = core.packageDefinition
    assert(pdefopt.isDefined)
    val pdef = pdefopt.get
    assert(pdef.name == "smqd-core")
    assert(pdef.plugins.exists(p => p.name == "thing2x-core-mqtt"))
    assert(pdef.plugins.exists(p => p.name == "thing2x-core-http"))
  }

  it should "filter a package by type" in {

    val spl = mgr.servicePluginDefinitions
    assert(spl.nonEmpty)
    assert(spl.exists(_.name == "thing2x-core-mqtt"))
    assert(spl.exists(_.clazz == classOf[MqttService]))
    assert(spl.exists(_.name == "thing2x-core-http"))
    assert(spl.exists(_.clazz == classOf[HttpService]))
  }

}
