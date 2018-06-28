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

package t2x.smqd.test

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * 2018. 6. 14. - Created by Kwon, Yeong Eon
  */
class RegistryPerf extends TestKit(ActorSystem("regperf", ConfigFactory.parseString(
  """
    |akka.actor.provider=local
  """.stripMargin).withFallback(ConfigFactory.load("smqd-ref.conf"))))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

/*
  system.actorOf(Props(classOf[RegistryCallbackManagerActor]), RegistryCallbackManagerActor.actorName)

  val registry: Registry = new LocalModeRegistry()
  val router: Router = new LocalModeRouter(registry)

  val act = system.actorOf(TestActors.blackholeProps)
  val echo = system.actorOf(TestActors.echoActorProps)

  override def beforeAll(): Unit = {
    val t0 = System.currentTimeMillis()
    for {
      a <- 1 to 10
      b <- 1 to 10
      c <- 1 to 10
    } {
      registry.subscribe(TPath.parseForFilter(f"$a%03d/$b%03d/$c%03d/#").get, echo)
    }
    val t1 = System.currentTimeMillis()
    println("register elapse time: "+(t1 - t0) +"ms.")
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "Matching filter" must {
    "single" in {
      val t0 = System.currentTimeMillis()
      val found = for {
        a <- 1 to 10
        m <- registry.filter(TPath.parseForTopic(f"$a%03d/001/001/001/0000000").get)
      } yield m

      assert(found.count(_ => true) == 10, "matching count")

      found.foreach{ m =>
        m.actor ! "message"
        expectMsg("message")
      }
      val t1 = System.currentTimeMillis()
      println("search elapse time: "+(t1 - t0) +"ms.")
    }
  }

  "An Echo actor" must {
    "send back messages unchanged" in {
      echo ! "hello world"
      expectMsg("hello world")
    }
  }
  */
}
