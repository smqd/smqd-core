package com.uangel.smqd

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FlatSpec
import t2x.smqd._
import scala.language.implicitConversions

/**
  * 2018. 6. 25. - Created by Kwon, Yeong Eon
  */
class OptionalConfigTest extends FlatSpec {
  val config: Config = ConfigFactory.parseString("""
    smqd {
      strSome = "some value"
      configSome = {
        key1 = value1
      }
    }
    """.stripMargin)

  "OptionalCofngi" should "String" in {
    var v: Option[String] = None

    v = config.getOptionString("smqd.strSome")
    assert(v.isDefined)

    v = config.getOptionString("smqd.not_exists_")
    assert(v.isEmpty)
  }

  it should "Config" in {
    var c: Option[Config] = None
    c =config.getOptionConfig("smqd.configSome")
    assert(c.isDefined)
  }
}
