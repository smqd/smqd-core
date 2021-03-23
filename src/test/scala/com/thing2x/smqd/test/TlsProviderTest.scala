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

import com.thing2x.smqd._
import com.thing2x.smqd.util.ConfigUtil._
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec

// 2018. 6. 26. - Created by Kwon, Yeong Eon

class TlsProviderTest extends AnyFlatSpec {

  "tsl provider" should "create from file" in {

    val conf = ConfigFactory
      .parseString("""
        | smqd {
        |   tls {
        |     storetype = jks
        |     keystore = ./src/test/tls/keygen/smqd-server.jks
        |     storepass = smqd.demo.key
        |     keypass = smqd.demo.key
        |   }
        | }
      """.stripMargin)
      .resolve()

    val tlsConf = conf.getOptionConfig("smqd.tls")
    assert(tlsConf.isDefined)

    val tlsProvider = TlsProvider(tlsConf.get)

    val engine = tlsProvider.sslEngine
    assert(engine.isDefined)
  }
}
