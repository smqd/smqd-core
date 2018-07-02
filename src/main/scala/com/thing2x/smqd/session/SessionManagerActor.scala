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
import com.thing2x.smqd.session.SessionActor.{ChallengeConnect, ChallengeConnectAccepted, ChallengeConnectDenied, ChallengeConnectResult}
import com.thing2x.smqd.session.SessionManagerActor._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */
object SessionManagerActor {
  val actorName = "sessions"

  case class CreateSession(ctx: SessionContext, cleanSession: Boolean, promise: Promise[CreateSessionResult])

  sealed trait CreateSessionResult
  case class SessionCreated(clientId: ClientId, sessionActor: ActorRef, hadPreviousSession: Boolean) extends CreateSessionResult
  case class SessionNotCreated(clientId: ClientId, reason: String) extends CreateSessionResult

  case class FindSession(clientId: ClientId, promise: Promise[FindSessionResult])

  sealed trait FindSessionResult
  case class SessionFound(clientId: ClientId, sessionActor: ActorRef) extends FindSessionResult
  case class SessionNotFound(clientId: ClientId) extends FindSessionResult
}

abstract class SessionManagerActor(smqd: Smqd, sstore: SessionStore) extends Actor with StrictLogging {

  import smqd.Implicit._

  private case class ChallengedSessionActorStopped(sessionActor: ActorRef, code: ()=>Unit)

  // find session actor of clientId
  protected def findSession(clientId: ClientId): Future[Option[ActorRef]]

  override def receive: Receive = {
    case ChiefActor.Ready =>
      sender ! ReadyAck

    case msg: CreateSession =>
      val clientId = msg.ctx.clientId
      val cleanSession = msg.cleanSession
      val promise = msg.promise

      findSession(clientId).onComplete {
        case Success(Some(sessionActor)) =>
          // [MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Server than the Server MUST
          // disconnect the existing Client
          //
          // [MQTT-3.2.2-1] If the Server accepts a connection with CleanSession set to 1, the Server MUST set
          // Session Present to 0 in the CONNACK packet in addition to setting a zero return code in CONNACK packet
          logger.trace(s"[$clientId] Session already exists, cleanSession: $cleanSession")
          val tryPromise = Promise[ChallengeConnectResult]()
          sessionActor ! ChallengeConnect(by = clientId, cleanSession, tryPromise)
          tryPromise.future map {
            case _: ChallengeConnectAccepted =>
              // wait until previous actor stop by watch().
              // then code for creating new session actor passed as a wachWith message
              context.watchWith(sessionActor, ChallengedSessionActorStopped(sessionActor, { () =>
                createSession0(clientId, msg.ctx, cleanSession, sessionPresent = true, msg.promise)
              }))
              context.stop(sessionActor)
            case _: ChallengeConnectDenied =>
              promise.success(SessionNotCreated(clientId, "Previous channel denied to give up"))
          }
        case Success(None) => // create new session and session store
          logger.trace(s"[$clientId] Creating Session...")
          createSession0(clientId, msg.ctx, cleanSession, sessionPresent = false, promise)
        case Failure(ex) =>
          logger.trace(s"[$clientId] Finding Session failed")
          promise.failure(ex)
      }

    case msg: ChallengedSessionActorStopped =>
      context.unwatch(msg.sessionActor)
      msg.code()

    case msg: FindSession =>
      findSession(msg.clientId).onComplete {
        case Success(Some(sessionActor)) =>
          msg.promise.success(SessionFound(msg.clientId, sessionActor))
        case Success(None) =>
          msg.promise.success(SessionNotFound(msg.clientId))
        case Failure(ex) =>
          msg.promise.failure(ex)
      }
  }

  private def createSession0(clientId: ClientId, ctx: SessionContext, cleanSession: Boolean, sessionPresent: Boolean, promise: Promise[CreateSessionResult]): Unit = {
    sstore.createSession(clientId, cleanSession).onComplete {
      case Success(stoken) =>
        val child = context.actorOf(Props(classOf[SessionActor], ctx, smqd, sstore, stoken), clientId.actorName)
        promise.success(SessionCreated(clientId, child, hadPreviousSession = if (cleanSession) false else sessionPresent))
      case Failure(ex) =>
        logger.error(s"[$clientId] SessionCreation failed, cleanSession: $cleanSession", ex)
        promise.success(SessionNotCreated(clientId, "Session Store Error"))
    }
  }
}

class LocalModeSessionManagerActor(smqd: Smqd, sstore: SessionStore) extends SessionManagerActor(smqd, sstore) with StrictLogging {
  import smqd.Implicit._
  override def findSession(clientId: ClientId): Future[Option[ActorRef]] = Future {
    val childName = clientId.actorName
    context.child(childName)
  }
}

class ClusterModeSessionManagerActor(smqd: Smqd, sstore: SessionStore) extends SessionManagerActor(smqd, sstore) with StrictLogging {
  import smqd.Implicit._
  override def findSession(clientId: ClientId): Future[Option[ActorRef]] = Future {
    val childName = clientId.actorName
    context.child(childName)
  }
}