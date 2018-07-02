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

import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */
object SessionManagerActor {
  val actorName = "sessions"

  case class CreateSession(ctx: SessionContext, cleanSession: Boolean, promise: Promise[SessionResult])
  case class FindSession(ctx: SessionContext, createIfNotExist: Boolean, promise: Promise[SessionResult])

  sealed trait SessionResult

  case class SessionFound(clientId: ClientId, sessionActor: ActorRef) extends SessionResult
  case class SessionNotFound(clientId: ClientId) extends SessionResult
  case class SessionCreated(clientId: ClientId, sessionActor: ActorRef) extends SessionResult
  case class SessionNotCreated(clientId: ClientId) extends SessionResult
}

class SessionManagerActor(smqd: Smqd, sstore: SessionStore) extends Actor with StrictLogging {

  import smqd.Implicit._

  override def receive: Receive = {
    case ChiefActor.Ready =>
      sender ! ReadyAck

    case msg: CreateSession =>
      val clientId = msg.ctx.clientId
      val childName = clientId.actorName
      context.child(childName) match {
        case Some(actor) =>
          logger.trace(s"[$clientId] **** Session already exists")
          // [MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Server than the Server MUST
          // disconnect the existing Client
          actor ! ForceDisconnect(s"[$clientId] *** new channel connected with same clientId")
          context.stop(actor)
        case None => // create new session and session store
          createSession0(clientId, msg.ctx, cleanSession = true, msg.promise)
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
            createSession0(clientId, msg.ctx, cleanSession = false, msg.promise)
          else
            sender ! SessionNotFound(clientId)
      }
  }

  private def createSession0(clientId: ClientId, ctx: SessionContext, cleanSession: Boolean, promise: Promise[SessionResult]): Unit = {
    val childName = clientId.actorName

    sstore.createSession(clientId, cleanSession).onComplete {
      case Success(stoken) =>
        val child = context.actorOf(Props(classOf[SessionActor], ctx, smqd, sstore, stoken), name = childName)
        promise.success(SessionCreated(clientId, child))
      case Failure(ex) =>
        logger.error(s"[$clientId] SessionCreation failed, cleanSession: $cleanSession", ex)
        if (cleanSession)
          promise.success(SessionNotFound(clientId))
        else
          promise.success(SessionNotCreated(clientId))
    }
  }
}
