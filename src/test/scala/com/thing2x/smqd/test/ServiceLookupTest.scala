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
import com.thing2x.smqd.SmqdBuilder
import com.thing2x.smqd.fault.DefaultFaultListener
import com.thing2x.smqd.protocol.DefaultProtocolListener
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

// 2018. 6. 28. - Created by Kwon, Yeong Eon

class ServiceLookupTest
    extends TestKit(
      ActorSystem(
        "service_lookup",
        ConfigFactory
          .parseString("""
    |akka.actor.provider=local
    |akka.cluster.seed-nodes=["akka.tcp://smqd@127.0.0.1:2551"]
    |
    |smqd.services=["core-protocol", "core-fault"]
  """.stripMargin)
          .withFallback(ConfigFactory.load("smqd-ref.conf"))
      )
    )
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with StrictLogging {

  private val smqd = new SmqdBuilder(system.settings.config)
    .setActorSystem(system)
    .build()

  override def beforeAll(): Unit = {
    smqd.start()
  }

  override def afterAll(): Unit = {
    smqd.stop()
    TestKit.shutdownActorSystem(system)
  }

  "Service lookup" must {
    "find core-api" in {
      smqd.service("core-protocol") match {
        case Some(s: DefaultProtocolListener) =>
          assert(s.name == "core-protocol")
        case _ =>
          fail("Service core-api not found")
      }
    }
    "find core-fault" in {
      smqd.service("core-fault") match {
        case Some(s: DefaultFaultListener) =>
          assert(s.name == "core-fault")
        case _ =>
          fail("service core-fault not found")
      }
    }
  }
}
