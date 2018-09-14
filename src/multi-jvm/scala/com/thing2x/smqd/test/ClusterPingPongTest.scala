package com.thing2x.smqd.test

import akka.actor.{Actor, Props}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll

// 2018. 9. 14. - Created by Kwon, Yeong Eon

class ClusterPingPongTestMultiJvmNode1 extends ClusterTestBase
class ClusterPingPongTestMultiJvmNode2 extends ClusterTestBase

object ClusterPingPongConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")

  private val node1Cfg = ConfigFactory.parseString(
    """
      | akka {
      |   remote.netty.tcp.port = 0
      | }
    """.stripMargin)

  private val node2Cfg = ConfigFactory.parseString(
    """
      | akka {
      |   remote.netty.tcp.port = 0
      | }
    """.stripMargin)

  nodeConfig(node1)(node1Cfg, debugConfig(true))
  nodeConfig(node2)(node2Cfg, debugConfig(true))
}

object ClusterTestBase {
  class Ponger extends Actor {
    def receive: Receive = {
      case "ping" => sender ! "pong"
    }
  }
}

class ClusterTestBase extends MultiNodeSpec(ClusterPingPongConfig)
  with ClusterTestSpec
  with BeforeAndAfterAll
  with ImplicitSender
  with StrictLogging {

  import ClusterPingPongConfig._
  import ClusterTestBase._

  override def initialParticipants: Int = roles.size

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
        expectMsg("pong")
        enterBarrier("finish")
      }

      runOn(node2) {
        system.actorOf(Props[Ponger], "ponger")
        enterBarrier("deployed")
        enterBarrier("finish")
      }
    }
  }
}