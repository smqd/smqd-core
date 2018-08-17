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

import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import akka.actor.{Actor, ActorRef, Timers}
import com.thing2x.smqd.QoS._
import com.thing2x.smqd.SessionStore.SessionStoreToken
import com.thing2x.smqd._
import com.thing2x.smqd.fault._
import com.thing2x.smqd.net.mqtt.MqttSessionContext
import com.thing2x.smqd.session.SessionActor._
import com.thing2x.smqd.session.SessionManagerActor.SessionActorPostStopNotification
import com.thing2x.smqd.util.ActorIdentifying
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.{ByteBuf, Unpooled}

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */

object SessionActor {

  case class NewSessionChallenge(by: ClientId, cleanSession: Boolean) extends Serializable

  sealed trait NewSessionChallengeResult extends Serializable
  case class NewSessionChallengeAccepted(clientId: ClientId) extends NewSessionChallengeResult
  case class NewSessionChallengeDenied(clientId: ClientId) extends NewSessionChallengeResult

  case class Subscription(topicName: String, qos: QoS, var grantedQoS: QoS = QoS.Failure)

  // the maximum QoS with which the Server can send Application Message to the Client
  case class Subscribe(subs: Seq[Subscription], promise: Promise[Seq[QoS]])
  case class Unsubscribe(subs: Seq[String], promise: Promise[Seq[Boolean]])

  case class InboundPublish(topicPath: TopicPath, qos: QoS, isRetain: Boolean, msg: Array[Byte], promise: Option[Promise[Boolean]])
  case class OutboundPublish(topicPath: TopicPath, qos: QoS, isRetain: Boolean, msg: Any)
  case class OutboundPublishAck(msgId: Int)
  case class OutboundPublishRec(msgId: Int)
  case class OutboundPublishComp(msgId: Int)
  case class ChannelClosed(clearSession: Boolean)
  case object InboundDisconnect
}

class SessionActor(sessionCtx: SessionContext, smqd: Smqd, sstore: SessionStore, stoken: SessionStoreToken)
  extends Actor with Timers with ActorIdentifying with StrictLogging {

  private val localMessageId: AtomicLong = new AtomicLong(1)
  private def nextMessageId: Int = math.abs((localMessageId.getAndIncrement() % 0xFFFF).toInt)

  private val noOfSubscription = new AtomicInteger()

  private var challengingActor: Option[ActorRef] = None

  override def preStart(): Unit = {
    sessionCtx.sessionStarted()
  }

  override def postStop(): Unit = {
    // notify session actor's death to session manager actor,
    // so that ddata can remove session actor's registration from ddata
    context.parent ! SessionActorPostStopNotification(sessionCtx.clientId, self)

    sstore.flushSession(stoken)
    sessionCtx.sessionStopped()
  }

  override def receive: Receive = {
    case Subscribe(subs, promise) =>   subscribe(subs, promise)
    case Unsubscribe(subs, promise) => unsubscribe(subs, promise)
    case ipub: InboundPublish =>        inboundPublish(ipub)
    case opub: OutboundPublish =>       outboundPublish(opub)
    case oack: OutboundPublishAck =>    outboundAck(oack)
    case orec: OutboundPublishRec =>    outboundRec(orec)
    case ocomp: OutboundPublishComp =>  outboundComp(ocomp)
    case ChannelClosed(clearSession) => channelClosed(clearSession)
    case InboundDisconnect =>           inboundDisconnect()
    case challenge: NewSessionChallenge => challengeChannel(sender, challenge)
  }

  private def challengeChannel(replyTo: ActorRef, challenge: NewSessionChallenge): Unit = {
    logger.trace(s"[${sessionCtx.clientId}] challenged new channel by ${challenge.by}")
    if (challengingActor.isEmpty && (sessionCtx.state == SessionState.ConnectAcked || sessionCtx.state == SessionState.Failed)) {
      // if requestor (SessionManager) is in the same local node, it sends promise directly
      // in other case (remote node), reply as normal message
      replyTo ! NewSessionChallengeAccepted(sessionCtx.clientId)
      // the actor picks its successor
      challengingActor = Some(replyTo)
    }
    else {
      // if the session actor is during the CONNECT process or has already successor(challengingAcotr),
      // makes other challengers failed.
      replyTo ! NewSessionChallengeDenied(sessionCtx.clientId)
    }
  }

  private def inboundDisconnect(): Unit = {
    sessionCtx.close("received Disconnect")
  }

  private def inboundPublish(ipub: InboundPublish): Unit = {

    import smqd.Implicit._

    // check if this client has permission to publish message on this topic
    //
    // [MQTT-3.3.5-2] If a Server implementation does not authorize a PUBLISH to be performed by a Client;
    // It has no way of informing that Client. It MUST either make a positive acknowledgement, according to the
    // normal QoS rules, or close the Network Connection.
    smqd.allowPublish(ipub.topicPath, sessionCtx.clientId, sessionCtx.userName).onComplete {
      // FIXME: asynchronous calls 'allowPublish()' can make messages to be delivered in dis-ordered
      case Success(canPublish) if canPublish =>
        if (ipub.isRetain) {
          // [MQTT-3.3.1-5] If the RETAIN flag is set to 1, in a PUBLISH Packet sent by a Client to Server,
          // the Server MUST store the Application Message and its QoS, so that it can be delivered to future subscribers
          // whose subscriptions match its topic name

          val isZeroSizePayload = ipub.msg.length == 0

          if (isZeroSizePayload) {
            // [MQTT-3.3.1-10] A PUBILSH Packet with a RETAIN flag set to 1 and playload containing zero bytes will be
            // processed as normal by the Server and sent to Client with subscription matching the topic name. Additionally
            // any existing retained message with the same topic name MUST be removed and any future subscribers for
            // the topic will not receive a retained message

            // [MQTT-3.3.1-11] A zero byte retained message MUST NOT be stored as a retained message on the Server

            // delete retained message
            smqd.unretain(ipub.topicPath)
          }
          else {
            // [MQTT-3.3.1-7] If the Server receives a QoS 0 message with the RETAIN flag set to 1 it MUST discard
            // any message previously retained for that topic. It SHOULD store the new QoS 0 message as the new retained
            // message for that topic, buf MAY choose to discard it at any time - if this happens there will be no
            // retained message for that topic

            // store retained message
            smqd.retain(ipub.topicPath, ipub.msg)
          }
        }
        else {
          // [MQTT-3.3.1-12] If the RETAIN flag is 0, in a PUBLISH Packet sent by a Client to Server, the Server
          // MUST NOT store the message and MUST NOT remove or replace any existing retained message
        }

        // inter-nodes routing messaging
        //
        // [MQTT-3.3.1-9] The server MUST set the RETAIN flag to 0 when a PUBLISH Packet is sent to a Client
        // because it matches an established subscription regardless of how the flag was set in the message it received
        smqd.publish(ipub.topicPath, ipub.msg)

        ipub.promise match {
          case Some(p) => p.success(true)
          case None =>
        }
      case _ =>
        smqd.notifyFault(InvalidTopicToPublish(sessionCtx.clientId.toString, ipub.topicPath.toString))
        sessionCtx.close(s"publishing message on prohibited topic ${ipub.topicPath.toString}")
    }
  }

  import com.thing2x.smqd.protocol._
  import spray.json._

  private def outboundPublish(opub: OutboundPublish): Unit = {
    val payload = opub.msg match {
      case a: Array[Byte] => Unpooled.wrappedBuffer(a)
      case b: ByteBuf => b
      case x: ProtocolNotification =>
        Unpooled.wrappedBuffer(x.toJson.toString.getBytes(StandardCharsets.UTF_8))
      case f: Fault =>
        Unpooled.wrappedBuffer(f.toJson.toString.getBytes(StandardCharsets.UTF_8))
      case x =>
        Unpooled.wrappedBuffer(x.toString.getBytes(StandardCharsets.UTF_8))
    }
    opub.qos match {
      case QoS.AtMostOnce =>
        //// SPEC: A PUBLISH packet MUST NOT contain a Packet Identifier if its QoS value is set to 0
        sessionCtx.writePub(opub.topicPath.toString, opub.qos, opub.isRetain, 0, payload)

      case QoS.AtLeastOnce => // wait ack
        val msgId = nextMessageId
        sstore.storeBeforeDelivery(stoken, opub.topicPath, opub.qos, opub.isRetain, msgId, payload)
        sessionCtx.writePub(opub.topicPath.toString, opub.qos, opub.isRetain, msgId, payload)

      case QoS.ExactlyOnce => // wait rec, comp
        val msgId = nextMessageId
        sstore.storeBeforeDelivery(stoken, opub.topicPath, opub.qos, opub.isRetain, msgId, payload)
        sessionCtx.writePub(opub.topicPath.toString, opub.qos, opub.isRetain, msgId, payload)
    }
  }

  private def outboundAck(oack: OutboundPublishAck): Unit = {
    sstore.deleteAfterDeliveryAck(stoken, oack.msgId)
  }

  private def outboundRec(orec: OutboundPublishRec): Unit = {
    sstore.updateAfterDeliveryAck(stoken, orec.msgId)
    sessionCtx.writePubRel(orec.msgId)
  }

  private def outboundComp(ocomp: OutboundPublishComp): Unit = {
    sstore.deleteAfterDeliveryComplete(stoken, ocomp.msgId)
  }

  private def subscribe(subs: Seq[Subscription], promise: Promise[Seq[QoS]]): Unit = {
    var retained = List.empty[RetainedMessage]

    import smqd.Implicit._

    val subscribeFutures = subs.map{ s =>
      // check if the topic name is valid
      TPath.parseForFilter(s.topicName) match {
        case Some(filterPath) =>
          smqd.allowSubscribe(filterPath, s.qos, sessionCtx.clientId, sessionCtx.userName).map { qos =>
            // check if subscription is allowed by registry
            (qos, s.topicName, if(qos == QoS.Failure) "NotAllowed" else "Allowed")
          }.recover {
            // make over for not allowed cases
            case x: Throwable => (QoS.Failure, s.topicName, x.getMessage)
            case _ => (QoS.Failure, s.topicName, "DelegateFailure")
          }.map { case (qos, topicName, msg) =>
            // actual subscription, only for topics that are passed allowance check point
            if (qos != QoS.Failure) {
              val finalQoS = smqd.subscribe(topicName, self, sessionCtx.clientId, qos)
              (finalQoS, topicName, if (finalQoS == QoS.Failure) "RegistryFailure" else msg)
            }
            else {
              (qos, topicName, msg)
            }
          }
        case _ =>
          Future((QoS.Failure, s.topicName, "InvalidTopic"))
      }
    }

    Future.sequence(subscribeFutures).onComplete{
      case Success(results) =>
        val acks = results.map{ case (qos, topic, reason) =>
          if (qos == QoS.Failure) {
            reason match {
              case "NotAllowed" =>       // subscription not allowed
                smqd.notifyFault(TopicNotAllowedToSubscribe(sessionCtx.clientId.toString, s"Subscribe not allowed: $topic"))
              case "InvalidTopic" =>     // failure if the topicName is unable to parse
                smqd.notifyFault(InvalidTopicNameToSubscribe(sessionCtx.clientId.toString, s"Invalid topic to subscribe: $topic"))
              case "DelegateFailure" =>  // subscription failure on registry delegate, so no retained messages
                smqd.notifyFault(UnknownErrorToSubscribe(sessionCtx.clientId.toString, s"Subscribe failed by delegate: $topic"))
              case "RegistryFailure" =>  // subscription failure on registry
                smqd.notifyFault(UnknownErrorToSubscribe(sessionCtx.clientId.toString, s"Subscribe filaed by registry"))
              case ex =>  // subscription failure, so no retained messages
                smqd.notifyFault(UnknownErrorToSubscribe(sessionCtx.clientId.toString, s"Subscribe failed $ex: $topic"))
            }
          }
          else {
            // protocol tracking may make endless echo, to prevent it, remove protocol handlers from pipeline
            if (topic == "$SYS/protocols" || topic.startsWith("$SYS/protocols/")) {
              sessionCtx match {
                case mqttCtx: MqttSessionContext =>
                  logger.info(s"[${sessionCtx.clientId}] echo cancelling for protocol notification")
                  mqttCtx.removeProtocolNotification()
                case _ =>
              }
            }

            // save subscription state
            sstore.saveSubscription(stoken, topic, qos)

            // [MQTT-3.3.1-6] When a new subscription is established, the last retained message, if any,
            // on each matching topic name MUST be sent to the subscriber
            retained = retained ++ smqd.retainedMessages(FilterPath(topic), qos)
          }
          qos
        }

        noOfSubscription.addAndGet(acks.count(p => p != QoS.Failure))

        // first, reply SUBACK
        promise.success(acks)
        //callback(acks)

        // then, sending retained messages
        //
        // [MQTT-3.3.1-8] When sending a PUBLISH packet to a Client the Server MUST set the RETAIN flag to 1
        // if a message is sent as a result of a new subscription being made by a Client
        retained.foreach{ case RetainedMessage(topicPath, qos, msg) =>
          self ! OutboundPublish(topicPath, qos, isRetain = true, msg)
        }

      case _ =>
        promise.failure(new RuntimeException("Subscription failued due to internal error"))
    }
  }

  private def unsubscribe(unsubs: Seq[String], promise: Promise[Seq[Boolean]]): Unit = {
    import smqd.Implicit._

    val future = Future {
      unsubs.map { topicName =>
        TPath.parseForFilter(topicName) match {
          case Some(filterPath) =>
            sstore.deleteSubscription(stoken, filterPath)
            smqd.unsubscribe(filterPath, self)
          case _=>
            // failure if the topicName is unable to parse
            smqd.notifyFault(InvalidTopicNameToUnsubscribe(sessionCtx.clientId.toString, topicName))
            false
        }
      }
    }

    future.onComplete {
      case Success(results) =>
        noOfSubscription.addAndGet(results.count(p => p) * -1)
        promise.success(results)
      case Failure(ex) =>
        promise.failure(ex)
    }
  }

  private def channelClosed(clearSession: Boolean): Unit = {
    logger.trace(s"[${sessionCtx.clientId}] channel closed, clearSession: $clearSession")
    if (noOfSubscription.get > 0)
      smqd.unsubscribe(self)
    context.stop(self)
  }
}
