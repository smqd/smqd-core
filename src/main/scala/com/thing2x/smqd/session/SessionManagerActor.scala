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
import com.thing2x.smqd.ChiefActor.ReadyAck
import com.thing2x.smqd._
import com.thing2x.smqd.session.SessionActor.ForceDisconnect
import com.thing2x.smqd.session.SessionManagerActor._
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success}

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */
object SessionManagerActor {
  val actorName = "sessions"

  case class CreateCleanSession(ctx: SessionContext)
  case class FindSession(ctx: SessionContext, createIfNotExist: Boolean)

  case class SessionFound(clientId: ClientId, sessionActor: ActorRef)
  case class SessionNotFound(clientIdId: ClientId)
  case class SessionCreated(clientId: ClientId, sessionActor: ActorRef)
}

class SessionManagerActor(smqd: Smqd, sstore: SessionStore) extends Actor with StrictLogging {

  import smqd.Implicit._

  override def receive: Receive = {
    case ChiefActor.Ready =>
      sender ! ReadyAck

    case msg: CreateCleanSession =>
      val clientId = msg.ctx.clientId
      val childName = clientId.actorName
      context.child(childName) match {
        case Some(actor) =>
          logger.trace(s"[$clientId] **** Session already exists")
          // [MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Server than the Server MUST
          // disconnect the existing Client
          context.watchWith(actor, msg)
          actor ! ForceDisconnect(s"[$clientId] *** new channel connected with same clientId")
          context.stop(actor)
        case None => // create new session and session store
          createSession0(sender, clientId, msg.ctx, cleanSession = true)
          logger.trace(s"[$clientId] **** Session created")
      }

    case msg: FindSession =>
      val clientId = msg.ctx.clientId
      val childName = clientId.actorName
      val createIfNotExist = msg.createIfNotExist

      context.child(childName) match {
        case Some(child) =>
          sender ! SessionFound(clientId, child)
        case _ =>
          if( createIfNotExist )
            createSession0(sender, clientId, msg.ctx, cleanSession = false)
          else
            sender ! SessionNotFound(clientId)
      }
  }

  private def createSession0(requestor: ActorRef, clientId: ClientId, ctx: SessionContext, cleanSession: Boolean): Unit = {
    val childName = clientId.actorName

    sstore.createSession(clientId, cleanSession).onComplete {
      case Success(stoken) =>
        val child = context.actorOf(Props(classOf[SessionActor], ctx, smqd, sstore, stoken), name = childName)
        requestor ! SessionCreated(clientId, child)
      case Failure(ex) =>
        logger.error(s"[$clientId] SessionCreation failed, cleanSession: $cleanSession", ex)
        requestor ! SessionNotFound(clientId)
    }
  }
}
