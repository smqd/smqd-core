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

package com.thing2x.smqd.test

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FlatSpec
import com.thing2x.smqd._

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
