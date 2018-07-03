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

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, LWWMap, LWWMapKey}
import akka.pattern.ask
import akka.util.Timeout
import com.thing2x.smqd.ChiefActor.ReadyAck
import com.thing2x.smqd._
import com.thing2x.smqd.session.SessionActor._
import com.thing2x.smqd.session.SessionManagerActor._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.{Failure, Success}

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */
object SessionManagerActor {
  val actorName = "sessions"

  case class CreateSession(ctx: SessionContext, cleanSession: Boolean, promise: Promise[CreateSessionResult])

  sealed trait CreateSessionResult
  case class CreatedSessionSuccess(clientId: ClientId, sessionActor: ActorRef, hadPreviousSession: Boolean) extends CreateSessionResult
  case class CreateSessionFailure(clientId: ClientId, reason: String) extends CreateSessionResult

  case class FindSession(clientId: ClientId, promise: Promise[FindSessionResult])

  sealed trait FindSessionResult
  case class FindSessionSuccess(clientId: ClientId, sessionActor: ActorRef) extends FindSessionResult
  case class FindSessionFailure(clientId: ClientId) extends FindSessionResult

  case class SessionActorPostStopNotification(clientId: ClientId, sessionActor: ActorRef)
}

abstract class SessionManagerActor(smqd: Smqd, sstore: SessionStore) extends Actor with StrictLogging {

  import smqd.Implicit._

  private case class ChallengedSessionActorTerminated(clientId: ClientId, promise: Promise[Boolean], previousClientId: ClientId)

  // find session actor of clientId
  protected def findSession(clientId: ClientId): Future[Option[ActorRef]]
  // register session actor so that other nodes can find it
  protected def registerSession(clientId: ClientId, sessionActor: ActorRef): Future[Boolean]
  // unregister session actor
  protected def unregisterSession(clientId: ClientId): Future[Boolean]

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
          logger.trace(s"[$clientId] session already exists, cleanSession: $cleanSession, previous actor: ${sessionActor.toString}")
          challengeConnect(clientId, cleanSession, sessionActor).onComplete {
            case Success(NewSessionChallengeAccepted(previousClientId)) =>
              logger.trace(s"[$clientId] previous channel $previousClientId agree to give up")
              // we don't know exact time of death, so need to "watch" it
              // wait until previous actor stop by watch().

              val r = for {
                _ <- watchActorDeath(clientId, sessionActor, previousClientId)
                result <- createSession0(clientId, msg.ctx, cleanSession, sessionPresent = true)
                _ <- registerSession(clientId, result.asInstanceOf[CreatedSessionSuccess].sessionActor)
              } yield {
                result
              }
              r.map (c => msg.promise.success(c))

            case Success(NewSessionChallengeDenied(previousClientId)) =>
              logger.trace(s"[$clientId] previous channel $previousClientId denied to give up")
              promise.success(CreateSessionFailure(clientId, s"Previous channel $previousClientId denied to give up"))

            case Failure(ex) =>
              logger.trace(s"[$clientId] previous session actor doesn't answer for challenge", ex)
              val r = for {
                result <- createSession0(clientId, msg.ctx, cleanSession, sessionPresent = false)
                _ <- registerSession(clientId, result.asInstanceOf[CreatedSessionSuccess].sessionActor)
              } yield result
              r.map(c => msg.promise.success(c))

            case m =>
              logger.error(s"[$clientId] previous session actor reponds unknown message", m.getClass.getName)
          }
        case Success(None) => // create new session and session store
          logger.trace(s"[$clientId] creating Session...")
          createSession0(clientId, msg.ctx, cleanSession, sessionPresent = false).onComplete {
            case Success(result) =>
              registerSession(clientId, result.asInstanceOf[CreatedSessionSuccess].sessionActor).onComplete {
                case Success(true) =>
                  msg.promise.success(result)
                case Success(false) =>
                  msg.promise.success(CreateSessionFailure(clientId, "registration failed"))
                case Failure(ex) =>
                  msg.promise.failure(ex)
              }
            case Failure(ex) =>
              msg.promise.failure(ex)
          }
        case Failure(ex) =>
          logger.trace(s"[$clientId] finding Session failed")
          promise.failure(ex)
      }

    case ChallengedSessionActorTerminated(clientId, promise, previousClientId) =>
      logger.trace(s"[$clientId] observed previous session $previousClientId terminated")
      promise.success(true)

    case msg: FindSession =>
      findSession(msg.clientId).onComplete {
        case Success(Some(sessionActor)) =>
          msg.promise.success(FindSessionSuccess(msg.clientId, sessionActor))
        case Success(None) =>
          msg.promise.success(FindSessionFailure(msg.clientId))
        case Failure(ex) =>
          msg.promise.failure(ex)
      }
    case SessionActorPostStopNotification(clientId, _) =>
      // whenever child session actor dies, remove it from ddata registry
      unregisterSession(clientId)
  }

  private def watchActorDeath(clientId: ClientId, actor: ActorRef, previousClientId: ClientId): Future[Boolean] = {
    logger.trace(s"[$clientId] watching death of $previousClientId")
    val promise = Promise[Boolean]()
    context.watchWith(actor, ChallengedSessionActorTerminated(clientId, promise, previousClientId))
    actor ! PoisonPill
    implicit val timeout: Timeout = 2.second
    promise.future.withTimeout(new TimeoutException(s"Timeout watching death of $previousClientId"))
  }

  /** ask exsting session actor to give up itself for new coming connection */
  protected def challengeConnect(clientId: ClientId, cleanSession: Boolean, sessionActor: ActorRef): Future[NewSessionChallengeResult] = {
    // if existing session actor is in remote scope use ask pattern
    val promise = Promise[NewSessionChallengeResult]()
    implicit val timeout: Timeout = 2.second
    val response = sessionActor ? NewSessionChallenge(clientId, cleanSession)
    response.onComplete {
      case Success(r: NewSessionChallengeResult) => promise.success(r)
      case Failure(ex) => logger.warn(s"[$clientId] challenged actor return exception", ex)
      case m => promise.failure(new TimeoutException(s"challenged actor has unexpected answer: ${m.toString}"))
    }
    promise.future.withTimeout(new TimeoutException("challenged actor has no answer"))
  }

  private def createSession0(clientId: ClientId, ctx: SessionContext, cleanSession: Boolean, sessionPresent: Boolean): Future[CreateSessionResult] = {
    logger.trace(s"[$clientId] creating actor")
    val promise = Promise[CreateSessionResult]()
    sstore.createSession(clientId, cleanSession).onComplete {
      case Success(stoken) =>
        val child = context.actorOf(Props(classOf[SessionActor], ctx, smqd, sstore, stoken), clientId.actorName)
        promise.success(CreatedSessionSuccess(clientId, child, hadPreviousSession = if (cleanSession) false else sessionPresent))
      case Failure(ex) =>
        logger.error(s"[$clientId] SessionCreation failed, cleanSession: $cleanSession", ex)
        promise.success(CreateSessionFailure(clientId, "Session Store Error"))
    }

    implicit val timeout: Timeout = 2.second
    promise.future.withTimeout(new TimeoutException(s"Timeout creating session actor $clientId"))
  }
}

class LocalModeSessionManagerActor(smqd: Smqd, sstore: SessionStore) extends SessionManagerActor(smqd, sstore) with StrictLogging {
  import smqd.Implicit._
  override def findSession(clientId: ClientId): Future[Option[ActorRef]] = Future {
    val childName = clientId.actorName
    context.child(childName)
  }

  override def registerSession(clientId: ClientId, sessionActor: ActorRef): Future[Boolean] = Future {
    true // there is nothing to do in non-cluster mode
  }

  override def unregisterSession(clientId: ClientId): Future[Boolean] = Future {
    true // there is nothing to do in non-cluster mode
  }
}

class ClusterModeSessionManagerActor(smqd: Smqd, sstore: SessionStore) extends SessionManagerActor(smqd, sstore) with StrictLogging {
  private val ddata = DistributedData(context.system).replicator
  private val SessionsKey = LWWMapKey[String, ActorRef]("smqd.sessions")

  private implicit val node: Cluster = smqd.cluster.get

  private val readConsistency = ReadMajority(timeout = 2.second)
  private val writeConsistency = WriteMajority(timeout = 2.second)

  import smqd.Implicit._

  override def preStart(): Unit = {
    ddata ! akka.cluster.ddata.Replicator.Subscribe(SessionsKey, self)
  }

  override def postStop(): Unit = {
    ddata ! akka.cluster.ddata.Replicator.Unsubscribe(SessionsKey, self)
  }

  override def receive: Receive = super[SessionManagerActor].receive orElse receive0

  def receive0: Receive = {
    case g @ GetSuccess(SessionsKey, reqOpt) =>
      reqOpt.get.asInstanceOf[(ClientId, Promise[Option[ActorRef]])] match {
        case (clientId, promise) =>
          logger.trace(s"[$clientId] ddata looking for: ${clientId.id}")
          promise.success(g.get(SessionsKey).get(clientId.id))
        case m =>
          logger.error("Unhandled message in replicator.GetSuccess: {}", m.getClass.getName)
      }
    case GetFailure(SessionsKey, reqOpt) =>
      reqOpt.get.asInstanceOf[(ClientId, Promise[Option[ActorRef]])] match{
        case (clientId, promise) =>
          logger.trace(s"[$clientId] ddata get failed")
          promise.success(None)
        case m =>
          logger.error("Unhandled message in replicator.GetFailure: {}", m.getClass.getName)
      }
    case NotFound(SessionsKey, reqOpt) =>
      reqOpt.get.asInstanceOf[(ClientId, Promise[Option[ActorRef]])] match {
        case (clientId, promise) =>
          logger.trace(s"[$clientId] ddata not found")
          promise.success(None)
        case m =>
          logger.error("Unhandled message in replicator.NotFound: {}", m.getClass.getName)
      }
    case UpdateSuccess(SessionsKey, reqOpt) =>
      reqOpt.get.asInstanceOf[(ClientId, Promise[Boolean], String)] match {
        case (clientId, promise, debug) =>
          logger.trace(s"[$clientId] ddata $debug")
          promise.success(true)
        case m =>
          logger.error(s"UpdateSuccess - unknown condition: ${m.getClass.getName}")
      }
    case UpdateTimeout(SessionsKey, reqOpt) =>
      reqOpt.get.asInstanceOf[(ClientId, Promise[Boolean], String)] match {
        case (clientId, promise, debug) =>
          logger.trace(s"[$clientId] ddata $debug")
          promise.success(true)
        case m =>
          logger.error(s"UpdateTimeout - unknown condition: ${m.getClass.getName}")
      }
  }

  override def findSession(clientId: ClientId): Future[Option[ActorRef]] = {
    logger.trace(s"[$clientId] finding existing session ${clientId.id}")
    val promise = Promise[Option[ActorRef]]
    ddata ! Get(SessionsKey, readConsistency, Some((clientId, promise)))

    implicit val timeout: Timeout = 2.second
    promise.future.withTimeout(new TimeoutException("Timeout finding session"))
  }

  override def registerSession(clientId: ClientId, sessionActor: ActorRef): Future[Boolean] = {
    val promise = Promise[Boolean]
    ddata ! Update(SessionsKey, LWWMap.empty[String, ActorRef], writeConsistency, Some((clientId, promise, "registered"))){ m =>
      logger.trace(s"[$clientId] ddata register (${clientId.id} -> ${sessionActor.toString})")
      m + (clientId.id, sessionActor)
    }

    implicit val timeout: Timeout = 2.second
    promise.future.withTimeout(new TimeoutException("Timeout register session"))
  }

  override def unregisterSession(clientId: ClientId): Future[Boolean] = {
    val promise = Promise[Boolean]
    ddata ! Update(SessionsKey, LWWMap.empty[String, ActorRef], writeConsistency, Some((clientId, promise, "unregistered"))){ m =>
      logger.trace(s"[$clientId] ddata unregister")
      m - clientId.id
    }

    implicit val timeout: Timeout = 2.second
    promise.future.withTimeout(new TimeoutException("Timeout unregister session"))
  }
}