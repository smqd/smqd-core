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

package com.thing2x.smqd.session

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd.ChiefActor.ReadyAck
import com.thing2x.smqd.session.SessionManagerActor._
import com.thing2x.smqd._

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */
object SessionManagerActor {
  val actorName = "sessions"

  case class CreateSession(ctx: SessionContext)
  case class FindSession(ctx: SessionContext, createIfNotExist: Boolean)

  case class SessionFound(sessionId: SessionId, sessionActor: ActorRef)
  case class SessionNotFound(sessionId: SessionId)
  case class SessionCreated(sessionId: SessionId, sessionActor: ActorRef)
}

class SessionManagerActor(smqd: Smqd) extends Actor with StrictLogging {

  override def receive: Receive = {
    case ChiefActor.Ready =>
      sender ! ReadyAck

    case msg: CreateSession =>
      context.child(msg.ctx.sessionId.actorName) match {
        case Some(actor) =>
          context.stop(actor)
          self forward msg
        case None =>
          val child = context.actorOf(Props(classOf[SessionActor], msg.ctx, smqd), name = msg.ctx.sessionId.actorName)
          sender ! SessionCreated(msg.ctx.sessionId, child)
      }

    case msg: FindSession =>
      val sessionId = msg.ctx.sessionId
      val childName = sessionId.actorName
      val createIfNotExist = msg.createIfNotExist

      context.child(childName) match {
        case Some(child) =>
          sender ! SessionFound(sessionId, child)
        case _ =>
          if( createIfNotExist ){
            val child = context.actorOf(Props(classOf[SessionActor], msg.ctx), name = childName)
            sender ! SessionCreated(sessionId, child)
          }
          else {
            sender ! SessionNotFound(sessionId)
          }
      }
  }
}
