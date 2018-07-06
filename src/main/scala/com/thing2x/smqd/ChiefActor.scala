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

package com.thing2x.smqd

import akka.actor.{Actor, Props}
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberLeft, UnreachableMember}
import akka.pattern.ask
import akka.util.Timeout
import com.thing2x.smqd.delivery.DeliveryManagerActor
import com.thing2x.smqd.fault.FaultNotificationManager
import com.thing2x.smqd.protocol.ProtocolNotificationManager
import com.thing2x.smqd.session.{ClusterModeSessionManagerActor, LocalModeSessionManagerActor, SessionManagerActor}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */
object ChiefActor {
  val actorName = "chief"

  case object Ready
  case object ReadyAck

  case object NodeInfoReq
}

import com.thing2x.smqd.ChiefActor._

class ChiefActor(smqd: Smqd, requestor: Requestor, registry: Registry, router: Router, retainer: Retainer, sstore: SessionStore)
  extends Actor with StrictLogging {

  override def preStart(): Unit = {
    context.actorOf(Props(classOf[FaultNotificationManager], smqd), FaultNotificationManager.actorName)
    context.actorOf(Props(classOf[ProtocolNotificationManager], smqd), ProtocolNotificationManager.actorName)
    context.actorOf(Props(classOf[DeliveryManagerActor]), DeliveryManagerActor.actorName)
    context.actorOf(Props(classOf[RegistryCallbackManagerActor], smqd), RegistryCallbackManagerActor.actorName)
    context.actorOf(Props(classOf[RequestManagerActor], smqd, requestor), RequestManagerActor.actorName)

    if (smqd.isClusterMode) {
      context.actorOf(Props(classOf[ClusterModeSessionManagerActor], smqd, sstore), SessionManagerActor.actorName)
      context.actorOf(Props(classOf[RoutesReplicator], smqd, router, registry), RoutesReplicator.actorName)
      context.actorOf(Props(classOf[RetainsReplicator], smqd, retainer), RetainsReplicator.actorName)
    }
    else {
      context.actorOf(Props(classOf[LocalModeSessionManagerActor], smqd, sstore), SessionManagerActor.actorName)
    }

    context.children.foreach{ child =>
      try {
        implicit val readyTimeout: Timeout = 3 second
        val future = child ? ChiefActor.Ready
        Await.result(future, readyTimeout.duration) match {
          case ChiefActor.ReadyAck =>
            logger.info(s"${child.path} ready")
        }
      }
      catch {
        case x: Throwable =>
          logger.error(s"${child.path} is NOT ready")
          throw x
      }
    }

    smqd.cluster match {
      case Some(cl) =>
        cl.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
      case _ =>
    }
  }

  override def postStop(): Unit = {
    smqd.cluster match {
      case Some(cl) =>
        cl.unsubscribe(self)
      case _ =>
    }
  }

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      smqd.setChiefActor(this)
      // smqd.subscribe("$SYS/chief/committee/#", self) // may need in the future
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case UnreachableMember(member) =>
      logger.info("Member detected as unreachable: {}", member)
    case MemberLeft(member) =>
      logger.info("Member detected as leaving", member)
      smqd.cluster match {
        case Some(cl) =>
          cl.down(member.address)
        case _ =>
      }
    case evt: MemberEvent =>
      logger.info("Member event: {}", evt)
    case props: Props =>
      logger.info("received actor props: {}", props.toString)

    case NodeInfoReq =>
      // todo actual api listener host port
      val node = smqd.cluster match {
        case Some(cl) => // cluster mode
          val leaderAddress = cl.state.leader
          val m = cl.selfMember
          NodeInfo(smqd.nodeName, "todo:some_api_address",
            if (m.address.hasGlobalScope) m.address.hostPort else smqd.nodeHostPort,
            m.status.toString,
            m.roles.map(_.toString),
            m.dataCenter,
            leaderAddress match {
              case Some(addr) => m.address == addr
              case _ => false
            })
        case None => // non-cluster mode
          NodeInfo(smqd.nodeName, "todo: Some_api_address", smqd.nodeHostPort,  "Up", Set.empty, "<non-cluster>", isLeader = true)
      }
      sender ! node
  }

  def nodeInfo: Future[Seq[NodeInfo]] = {
    import smqd.Implicit._
    val actorSelections = smqd.cluster match {
      case Some(cl) => // cluster mode
        cl.state.members.map{ m =>
          context.system.actorSelection(m.address.toString+"/user/"+actorName)
        }.toSeq

      case None => // non-cluster mode
        Seq(context.system.actorSelection("/user/"+actorName))
    }

    val annsAsk = actorSelections.map{ selection =>
      implicit val timeout: Timeout = 3.second
      (selection ? NodeInfoReq).asInstanceOf[Future[NodeInfo]]
    }


    Future.sequence(annsAsk)
  }

  def nodeInfo(nodeName: String): Future[NodeInfo] = Future {
    NodeInfo(smqd.nodeName, "todo: Some_api_address", smqd.nodeHostPort,  "Up", Set.empty, "<non-cluster>", isLeader = true)
  }
}
