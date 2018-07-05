package com.thing2x.smqd.plugin.test

import com.thing2x.smqd.plugin.PluginManager
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.FlatSpec

import scala.sys.process._

/**
  * 2018. 7. 4. - Created by Kwon, Yeong Eon
  */
class PluginManagerTest extends FlatSpec with StrictLogging {
  private val pwd = "pwd".!!.trim
  logger.debug(s"PWD: $pwd")

  val mgr = new PluginManager(null, "target/scala-2.12/", s"file://$pwd/src/test/resources/smqd-plugins-manifest-custom.conf")

  "PluginManager" should "initialize" in {
    val repos = mgr.repositoryDefinitions
    repos.foreach { repo =>
      val inst = if (repo.installed) "installed" else if (repo.installable) "installable" else "not installable"
      logger.info(s"Repo '${repo.name}' is $inst")

      val pkgs = repo.packageDefinition
      pkgs.foreach { pkg =>
        logger.info(s"     plugins: ${pkg.plugins.map(p => p.name).mkString(", ")}")
      }
    }
  }

  it should "filter a package by name" in {

    // check repository def
    val rpopt = mgr.repositoryDefinition("Fake plugins")
    assert(rpopt.isDefined)
    val rp = rpopt.get

    assert(rp.name == "Fake plugins")
    assert(rp.location.toString == "https://fake.com/smqd-fake_2.12.0.1.0.jar")
    assert(rp.provider == "www.smqd-test.com")

    // check package def
    val pksopt = rp.packageDefinition
    assert (pksopt.isDefined)
    val pks = pksopt.get
    assert(pks.name == "Fake plugins" )
    assert(pks.repository.installable)

    // check repo of package def
    val repo = pks.repository
    assert(repo.name == "Fake plugins")
    assert(repo.location.toString == "https://fake.com/smqd-fake_2.12.0.1.0.jar")
    assert(repo.provider == "www.smqd-test.com")

    val ps = pks.plugins

    val take1 = ps.head
    val take2 = ps.last

    assert(take1.name == "Take one plugin")
    assert(take2.name == "Take two plugin")
    assert(take1.clazz.getName == classOf[TakeOnePlugin].getName)
    assert(take2.clazz.getName == classOf[TakeTwoPlugin].getName)
  }

  it should "filter a package by type" in {

    val spl = mgr.servicePluginDefinitions
    assert(spl.size == 1)
    assert(spl.head.name == "Take one plugin")

    val bpl = mgr.bridgePluginDefinitions
    assert(bpl.size == 1)
    assert(bpl.head.name == "Take two plugin")
  }

  it should "produce service plugin - anon" in {
    val spl = mgr.servicePluginDefinitions

    val svc = mgr.createPlugin("one", spl.head)
    svc.start()

    svc.stop()
  }

  it should "produce service plugin - named" in {
    val pds = mgr.pluginDefinitions("Take one plugin")
    assert(pds.size == 1)
    val svc = mgr.createPlugin("one", pds.head)
    svc.start()
    svc.stop()
  }

  it should "produce bridge plugin" in {
    val spl = mgr.bridgePluginDefinitions

    val svc = mgr.createPlugin("two", spl.head)
    svc.start()

    svc.stop()
  }

}
