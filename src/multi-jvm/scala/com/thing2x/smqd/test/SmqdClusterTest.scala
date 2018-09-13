package com.thing2x.smqd.test

import akka.actor.{Actor, Props}
import akka.cluster.Cluster
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.thing2x.smqd.{SmqdBuilder, TopicPath}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

// 2018. 9. 12. - Created by Kwon, Yeong Eon

object SmqdClusterTestConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")

  private val commonCfg = ConfigFactory.parseString(
    """
      | smqd {
      |   actor_system_name = "SmqdClusterTest"
      |   services = ["core-protocol"]
      |
      |   core-protocol.config.coloring = true
      |   registry.verbose = false
      |   router.verbose = false
      | }
      | akka {
      |   cluster.seed-nodes=["akka.tcp://SmqdClusterTest@127.0.0.1:2001"]
      |   cluster.min-nr-of-members = 2
      |   test.conductor.barrier-timeout = 5s
      | }
    """.stripMargin)

  private val node1Cfg = ConfigFactory.parseString(
    """
      | smqd {
      |   node_name = "node1"
      | }
      | akka {
      |   actor.provider=cluster
      |   remote.netty.tcp.port = 2001
      | }
    """.stripMargin)

  private val node2Cfg = ConfigFactory.parseString(
    """
      | smqd {
      |   node_name = "node1"
      | }
      | akka {
      |   actor.provider=cluster
      |   remote.netty.tcp.port = 2002
      | }
    """.stripMargin)

  private val refCfg = ConfigFactory.parseResources("smqd-ref.conf")

  nodeConfig(node1)(node1Cfg, commonCfg, refCfg, debugConfig(true))
  nodeConfig(node2)(node2Cfg, commonCfg, refCfg, debugConfig(true))
}

class SmqdClusterTestMultiJvmNode1 extends SmqdClusterTest
class SmqdClusterTestMultiJvmNode2 extends SmqdClusterTest

object SmqdClusterTest {

  class Ponger extends Actor {
    def receive: Receive = {
      case "ping" => sender ! "pong"
    }
  }

  class Subscriber extends Actor with StrictLogging {
    def receive: Receive = {
      case (topic, msg) =>
        logger.info(s"==========> ${topic.toString}: $msg")
    }
  }
}

class SmqdClusterTest extends MultiNodeSpec(SmqdClusterTestConfig)
  with ClusterTestSpec
  with BeforeAndAfterAll
  with ImplicitSender
  with StrictLogging {

  import SmqdClusterTest._
  import SmqdClusterTestConfig._

  private val smqd = new SmqdBuilder(system.settings.config).setActorSystem(system).setServices(Map.empty).build()

  override def initialParticipants: Int = roles.size

  override def beforeAll(): Unit = {
    super.multiNodeSpecBeforeAll()
    smqd.start()
  }

  override def afterAll(): Unit = {
    smqd.stop()
    super.multiNodeSpecAfterAll()
  }

  "PingPongTest" must {
    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    // simple ping/pong message test between remote actors
    "send to and receive from a remote node" in {
      runOn(node1) {
        enterBarrier("deployed")

        val ponger = system.actorSelection(node(node2) / "user" / "ponger")
        ponger ! "ping"
        expectMsg(10.seconds, "pong")
      }

      runOn(node2) {
        system.actorOf(Props[Ponger], "ponger")
        enterBarrier("deployed")
      }
    }
  }

  "Subscriber with Actor" must {
    runOn(node1) {
      "subscribe to test/actor" in {
        val sub = system.actorOf(Props[Subscriber], "subscriber")
        smqd.subscribe("test/actor", sub)
        enterBarrier("actor_sub_ready")
        Thread.sleep(1000)
        smqd.publish("test/actor", "Are you ready?")
      }
    }

    "be tested" in {
      runOn(node2) {
        enterBarrier("actor_sub_ready")
      }
      enterBarrier("actor_pub")
    }
  }

  "Subscriber with PartialFunction" must {
    val done = Promise[Boolean]

    // node1 subscribe to 'test/hello' topic
    runOn(node1) {
      "subscribe to test/hello" in {
        smqd.subscribe("test/hello"){
          case (_, "READY") =>
            logger.info(s"==========> READY.")
            enterBarrier("sub_pub_ready")
          case (_, "FIN") =>
            logger.info(s"==========> FIN.")
            enterBarrier("finish")
            done.success(true)
          case (topic: TopicPath, msg: String) =>
            logger.info(s"==========> ${topic.toString}: $msg")
        }
        Thread.sleep(1000)
        smqd.publish("test/hello", "READY")
        logger.info("sending........ READY")
      }
    }

    // node2 publish a message to 'test/hello' topic
    runOn(node2) {
      "publish a message to test/hello" in {
        enterBarrier("sub_pub_ready")
        logger.info(s"----------- publishing ${smqd.isClusterMode}")
        smqd.publish("test/hello", "Hello Message from node2")
        smqd.publish("test/hello", "FIN")
        enterBarrier("finish")
        done.success(true)
      }
    }

    "finish" in {
      Await.result(done.future, 5.seconds)
    }
  }
}

