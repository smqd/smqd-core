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

package t2x.smqd.session

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import akka.actor.{Actor, Timers}
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf
import t2x.smqd.QoS._
import t2x.smqd._
import t2x.smqd.fault._
import t2x.smqd.net.mqtt.MqttSessionContext
import t2x.smqd.session.SessionActor._
import t2x.smqd.util.ActorIdentifying

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * 2018. 6. 2. - Created by Kwon, Yeong Eon
  */

object SessionActor {

  case class Subscription(topicName: String, qos: QoS, var grantedQoS: QoS = QoS.Failure)

  // the maximum QoS with which the Server can send Application Message to the Client
  case class Subscribe(subs: Seq[Subscription], promise: Promise[Seq[QoS]])
  case class Unsubscribe(subs: Seq[String], promise: Promise[Seq[Boolean]])

  case class InboundPublish(topicPath: TopicPath, qos: QoS, isRetain: Boolean, msg: ByteBuf, promise: Promise[Unit])
  case class OutboundPublish(topicPath: TopicPath, qos: QoS, isRetain: Boolean, msg: Any)
  case class OutboundPublishAck(msgId: Int)
  case class OutboundPublishRec(msgId: Int)
  case class OutboundPublishComp(msgId: Int)
  case class ChannelClosed(clearSession: Boolean)

  case object UpdateTimer

  private case object TimeoutKey
  private case object Timeout
}

class SessionActor(ctx: SessionContext, smqd: Smqd) extends Actor with Timers with ActorIdentifying with StrictLogging {

  private val localMessageId: AtomicLong = new AtomicLong(1)
  private def nextMessageId: Int = math.abs((localMessageId.getAndIncrement() % 0xFFFF).toInt)

  private val noOfSubscription = new AtomicInteger()

  override def preStart(): Unit = {
    // private var deliveryManager: ActorRef = _
    // deliveryManager = identifyActor("user/"+ChiefActor.actorName+"/"+DeliveryManagerActor.actorName)

    ctx.sessionStarted()
    // session timeout 처리를 위한 타이머
    updateTimer()
  }

  override def postStop(): Unit = {
    ctx.sessionStopped()
  }

  private def timeout(): Unit = {
    ctx.sessionTimeout()
  }

  override def receive: Receive = {
    case Subscribe(subs, promise) =>   subscribe(subs, promise)
    case Unsubscribe(subs, promise) => unsubscribe(subs, promise)
    case ipub: InboundPublish =>        inbound(ipub)
    case opub: OutboundPublish =>       outbound(opub)
    case oack: OutboundPublishAck =>    outboundAck(oack)
    case orec: OutboundPublishRec =>    outboundRec(orec)
    case ocomp: OutboundPublishComp =>  outboundComp(ocomp)
    case ChannelClosed(clearSession) => channelClosed(clearSession)
    case UpdateTimer =>                 updateTimer()
    case Timeout =>                     timeout()
    case _ =>
  }

  private def inbound(ipub: InboundPublish): Unit = {

    if (ipub.isRetain) {
      // [MQTT-3.3.1-5] If the RETAIN flag is set to 1, in a PUBLISH Packet sent by a Client to Server,
      // the Server MUST store the Application Message and its QoS, so that it can be delivered to future subscribers
      // whose subscriptions match its topic name

      val isZeroSizePayload = ipub.msg.readableBytes() == 0

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
        smqd.retain(ipub.topicPath, ipub.msg.copy)
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

    ipub.promise.success(Unit)
  }

  import spray.json._
  import t2x.smqd.protocol._

  private def outbound(opub: OutboundPublish): Unit = {
    val msgId = nextMessageId
    val msg = opub.msg match {
      case b: ByteBuf => b
      case x: ProtocolNotification =>
        io.netty.buffer.Unpooled.wrappedBuffer(x.toJson.toString.getBytes("utf-8"))
      case f: Fault =>
        io.netty.buffer.Unpooled.wrappedBuffer(f.toJson.toString.getBytes("utf-8"))
      case _ =>
        io.netty.buffer.Unpooled.wrappedBuffer(opub.msg.toString.getBytes("utf-8"))
    }
    opub.qos match {
      case QoS.AtMostOnce =>
        ctx.deliver(opub.topicPath.toString, opub.qos, opub.isRetain, msgId, msg)

      case QoS.AtLeastOnce => // wait ack
        ctx.deliver(opub.topicPath.toString, opub.qos, opub.isRetain, msgId, msg)

      case QoS.ExactlyOnce => // wait rec, comp
        ctx.deliver(opub.topicPath.toString, opub.qos, opub.isRetain, msgId, msg)
    }
  }

  private def outboundAck(oack: OutboundPublishAck): Unit = {
    logger.trace(s"[${ctx.sessionId}] Message Ack: (mid: ${oack.msgId})")
  }

  private def outboundRec(orec: OutboundPublishRec): Unit = {
    logger.trace(s"[${ctx.sessionId}] Message Rec: (mid: ${orec.msgId})")
  }

  private def outboundComp(ocomp: OutboundPublishComp): Unit = {
    logger.trace(s"[${ctx.sessionId}] Message Comp: (mid: ${ocomp.msgId})")
  }

  private def subscribe(subs: Seq[Subscription], promise: Promise[Seq[QoS]]): Unit = {
    var retained = List.empty[RetainedMessage]

    import smqd.gloablDispatcher

    val allowFuture = subs.map{ s =>

      // check if the topic name is valid
      TPath.parseForFilter(s.topicName) match {
        case Some(filterPath) =>
          smqd.allowSubscribe(filterPath, ctx.sessionId, ctx.userName).map { allow =>
            // check if subscription is allowed by registry
            if (allow) (s.qos, s.topicName, "Allowed")
            else (QoS.Failure, s.topicName, "NotAllowed")
          }.recover {
            // make over for not allowed cases
            case x: Throwable => (QoS.Failure, s.topicName, x.getMessage)
            case _ => (QoS.Failure, s.topicName, "DelegateFailure")
          }.map { case (qos, topicName, msg) =>
            // actual subscription, only for topics that are passed allowance check point
            if (qos != QoS.Failure) {
              val finalQoS = smqd.subscribe(topicName, self, ctx.sessionId, qos)
              (finalQoS, topicName, if (finalQoS == QoS.Failure) "Subscribe failed by registry" else msg)
            }
            else {
              (qos, topicName, msg)
            }
          }
        case _ =>
          Future((QoS.Failure, s.topicName, "InvalidTopic"))
      }
    }

    Future.sequence(allowFuture).onComplete{
      case Success(results) =>
        val acks = results.map{ case (qos, topic, reason) =>
          if (qos == QoS.Failure) {
            reason match {
              case "NotAllowed" =>       // subscription not allowed
                smqd.notifyFault(TopicNotAllowedToSubscribe(ctx.sessionId.toString, s"Subscribe not allowed: $topic"))
              case "InvalidTopic" =>     // failure if the topicName is unable to parse
                smqd.notifyFault(InvalidTopicNameToSubscribe(ctx.sessionId.toString, s"Invalid topic to subscribe: $topic"))
              case "DelegateFailure" =>  // subscription failure, so no retained messages
                smqd.notifyFault(UnknownErrorToSubscribe(ctx.sessionId.toString, s"Subscribe failed by Delegate: $topic"))
              case ex =>  // subscription failure, so no retained messages
                smqd.notifyFault(UnknownErrorToSubscribe(ctx.sessionId.toString, s"Subscribe failed $ex: $topic"))
            }
          }
          else {
            // protocol tracking may make endless echo, to prevent it, remove protocol handlers from pipeline
            if (topic == "$SYS/protocols" || topic.startsWith("$SYS/protocols/")) {
              ctx match {
                case mqttCtx: MqttSessionContext =>
                  logger.info(s"[${mqttCtx.sessionId.toString} echo cancelling for protocol notification")
                  mqttCtx.cancelProtocolNotification()
                case _ =>
              }
            }
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
    import smqd.gloablDispatcher

    val future = Future {
      unsubs.map { topicName =>
        TPath.parseForFilter(topicName) match {
          case Some(filterPath) =>
            smqd.unsubscribe(filterPath, self)
          case _=>
            // failure if the topicName is unable to parse
            smqd.notifyFault(InvalidTopicNameToUnsubscribe(ctx.sessionId.toString, topicName))
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

  private def updateTimer(): Unit = {
    //logger.trace(s"[${ctx.sessionId}] Session timer updated")
    timers.startSingleTimer(TimeoutKey, Timeout, (ctx.keepAliveTimeSeconds*1.5) seconds)
  }

  private def channelClosed(clearSession: Boolean): Unit = {
    logger.trace(s"[${ctx.sessionId}] channel closed, clearSession: $clearSession")
    if (noOfSubscription.get > 0)
      smqd.unsubscribe(self)
    context.stop(self)
  }
}
