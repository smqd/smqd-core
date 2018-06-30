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

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import com.thing2x.smqd.SmqdBuilder
import com.thing2x.smqd.net.http.HttpService

/**
  * 2018. 6. 28. - Created by Kwon, Yeong Eon
  */
class ServiceLookupTest extends TestKit(ActorSystem("smqd", ConfigFactory.parseString(
  """
    |akka.actor.provider=local
    |akka.cluster.seed-nodes=["akka.tcp://smqd@127.0.0.1:2551"]
    |
    |smqd.services=["core-api", "core-fault"]
  """.stripMargin).withFallback(ConfigFactory.load("smqd-ref.conf"))))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with StrictLogging {

  val smqd = new SmqdBuilder(system.settings.config)
    .setActorSystem(system)
    .build()
  smqd.start()

  "Service lookup" must {
    "find core-api" in {
      smqd.service("core-api") match {
        case Some(httpService: HttpService) =>
          assert(httpService.routes != null)
        case _ =>
          fail("Service core-api not found")
      }
    }
  }
}
