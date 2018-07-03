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
import com.thing2x.smqd.replica.{ClusterModeReplicationActor, LocalModeReplicationActor, ReplicationActor}
import com.thing2x.smqd.session.{ClusterModeSessionManagerActor, LocalModeSessionManagerActor, SessionManagerActor}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */
object ChiefActor {
  val actorName = "chief"

  case object Ready
  case object ReadyAck
}

import com.thing2x.smqd.ChiefActor._

class ChiefActor(smqd: Smqd, registry: Registry, router: Router, retainer: Retainer, sstore: SessionStore)
  extends Actor with StrictLogging {

  override def preStart(): Unit = {
    context.actorOf(Props(classOf[FaultNotificationManager], smqd), FaultNotificationManager.actorName)
    context.actorOf(Props(classOf[ProtocolNotificationManager], smqd), ProtocolNotificationManager.actorName)
    context.actorOf(Props(classOf[DeliveryManagerActor]), DeliveryManagerActor.actorName)
    context.actorOf(Props(classOf[RegistryCallbackManagerActor]), RegistryCallbackManagerActor.actorName)
    context.actorOf(Props(classOf[RequestManagerActor], smqd), RequestManagerActor.actorName)

    if (smqd.isClusterMode) {
      context.actorOf(Props(classOf[ClusterModeSessionManagerActor], smqd, sstore), SessionManagerActor.actorName)
      val localRouter = context.actorOf(Props(classOf[ClusterAwareLocalRouter], registry), ClusterAwareLocalRouter.actorName)
      context.actorOf(Props(classOf[ClusterModeReplicationActor], router, retainer, localRouter), ReplicationActor.actorName)
    }
    else {
      context.actorOf(Props(classOf[LocalModeSessionManagerActor], smqd, sstore), SessionManagerActor.actorName)
      context.actorOf(Props(classOf[LocalModeReplicationActor]), ReplicationActor.actorName)
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
  }
}
