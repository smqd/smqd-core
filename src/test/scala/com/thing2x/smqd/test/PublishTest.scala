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

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import com.thing2x.smqd._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success}

// 2018. 6. 18. - Created by Kwon, Yeong Eon

object PublishTest {

  class SubsribeActor(origin: ActorRef) extends Actor with StrictLogging {
    override def receive: Receive = {
      case (topic: TopicPath, msg: Any) =>
        // logger.info(s"==-==> ${topic} ${msg.toString}")
        origin ! msg
    }
  }

  class ServerActor(smqd: Smqd) extends Actor with StrictLogging {
    override def receive: Receive = {
      case (topicPath: TopicPath, ResponsibleMessage(replyTo, msg)) =>
        logger.info(s"Received a request from ${topicPath.toString}, replyTo:${replyTo.toString} msg: $msg")
        if (msg.toString.startsWith("Hello"))
          smqd.publish(replyTo, msg)
        else if (msg.toString.startsWith("FailMe")) // reply with Throwable for testing error case
          smqd.publish(replyTo, new RuntimeException("failure test"))
      // else case, do not reply for testing timeout
      case m =>
        logger.info("Unknown message: "+m)
    }
  }
}

class PublishTest extends TestKit(ActorSystem("pubtest", ConfigFactory.parseString(
  """
    |akka.actor.provider=local
    |akka.cluster.seed-nodes=["akka.tcp://smqd@127.0.0.1:2551"]
  """.stripMargin).withFallback(ConfigFactory.load("smqd-ref.conf"))))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with StrictLogging {

  val smqd = new SmqdBuilder(system.settings.config)
    .setActorSystem(system)
    .setServices(Map.empty)
    .build()

  override def beforeAll(): Unit = {
    smqd.start()
  }

  override def afterAll(): Unit = {
    smqd.stop()
    TestKit.shutdownActorSystem(system)
  }

  "Callback Subscription" must {
    "callback - partial function must work" in {
      val origin = self
      val subr = smqd.subscribe("registry/test/pf/+/temp"){
        case (topic, msg) =>
          //logger.info(s"==p==> ${topic} ${msg}")
          origin ! msg
      }

      1 to 100 foreach { i =>
        val msg = s"Hello World - $i"
        smqd.publish(s"registry/test/pf/$i/temp", msg)
        expectMsg(msg)
      }

      smqd.unsubscribe(subr)
    }

    "callback - function must work" in {
      val origin = self
      def callback(topic: TopicPath, msg: Any): Unit = {
        //logger.info(s"==m==> ${topic} ${msg}")
        origin ! msg
      }

      val subr = smqd.subscribe("registry/test/callback/+/temp", callback _ )

      1 to 100 foreach { i =>
        val msg = s"Hello World - $i"
        smqd.publish(s"registry/test/callback/$i/temp", msg)
        expectMsg(msg)
      }

      smqd.unsubscribe(subr)
    }
  }

  val subscribeActor = system.actorOf(Props(classOf[PublishTest.SubsribeActor], testActor), "echo")

  "Actor Subscription" must {
    "actor must work" in {
      smqd.subscribe("registry/test/actor/#", subscribeActor)

      1 to 100 foreach { i =>
        val msg = s"Hello World - $i"
        smqd.publish(s"registry/test/actor/$i/temp", msg)
        expectMsg(msg)
      }

      smqd.unsubscribe(subscribeActor)
    }
  }

  //val echo = system.actorOf(TestActors.echoActorProps)
  val serverActor = system.actorOf(Props(classOf[PublishTest.ServerActor], smqd), "server")

  "Request & Response" must {
    smqd.subscribe("request/func", serverActor)

    import smqd.Implicit._
    implicit val timeout: Timeout = 1 second

    "success case" in {

      val f1 = smqd.request("request/func", classOf[String], "Hello")
      Await.result(f1, timeout.duration)
      f1.onComplete {
        case Success(str) =>
          logger.info("Ok Responsed: {}", str)
        case Failure(ex) =>
          logger.info("exception", ex)
          fail()
      }
    }

    "failure case" in {
      intercept[Throwable] {
        val f2 = smqd.request("request/func", classOf[String], "FailMe")
        Await.result(f2, timeout.duration)
        //Assertions.assertThrows()
        f2.onComplete {
          case Success(_) =>
            logger.info("fail for waiting failure case")
            fail() // wait for failed case
          case Failure(_) =>
            logger.info("success for waiting failure case")
        }
      }
    }

    "timeout case" in {
      intercept[Throwable] {
        val f3 = smqd.request("request/func", classOf[String], msg = "Timeout")
        Await.result(f3, 1 seconds)
        f3.onComplete {
          case Success(_) =>
            logger.info("fail for waiting failure case")
            fail() // wait for failed case
          case Failure(_) =>
            logger.info("success for waiting failure case")
        }
      }
    }
  }
}

