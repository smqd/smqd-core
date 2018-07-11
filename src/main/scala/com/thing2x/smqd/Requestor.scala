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

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.util.Timeout
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps


// 2018. 6. 20. - Created by Kwon, Yeong Eon

class Requestor extends StrictLogging {

  def request[T](topicPath: TopicPath, msg: Any)(implicit ec: ExecutionContext, timeout: Timeout): Future[T] = {
    val p = Promise[T]()
    requestManager ! RequestMessage(topicPath, msg, p, timeout)
    p.future
  }

  private var requestManager: ActorRef = _

  private[smqd] def setRequestManager(rm: ActorRef): Unit = {
    requestManager = rm
  }
}

case class RequestMessage[T](topicPath: TopicPath, message: Any, promise: Promise[T], timeout: Timeout)
case class ResponsibleMessage(replyTo: TopicPath, message: Any) {
  /** Java API **/
  def getReplyTo: TopicPath = replyTo

  /** Java API **/
  def getMessage: Any = message
}

object RequestManagerActor {
  val actorName = "requestors"

  case object Check
  case class Waiting(promise: Promise[Any], dest: String, requestTime: Long, timeout: Long)
}

import com.thing2x.smqd.RequestManagerActor._

class RequestManagerActor(smqd: Smqd, requestor: Requestor) extends Actor with StrictLogging {


  private val reqIdGenerator = new AtomicLong()

  private val waitingBoard = mutable.HashMap[Long, Waiting]()

  private var scheduler: Cancellable = _

  private val responsePrefix: String = "$SYS/requestors/" + smqd.nodeName + "/"
  private val reponseFilter: FilterPath = FilterPath(responsePrefix + "#")

  override def preStart(): Unit = {
    import smqd.Implicit._
    scheduler = context.system.scheduler.schedule(1 second, 1 second, self, Check)
    // subscribe topic to receive the reponse message
    Future { // need to be a different thread
      smqd.subscribe(reponseFilter, self)
    }
  }

  override def postStop(): Unit = {
    import smqd.Implicit._
    Future { // need to be a different thread
      smqd.unsubscribe(reponseFilter, self)
    }
    scheduler.cancel()
  }

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      requestor.setRequestManager(self)
      sender ! ReadyAck
  }

  def receive0: Receive = {

    // request message from requester
    case RequestMessage(topic, msg, promise, timeout) =>
      val reqId = reqIdGenerator.getAndIncrement()
      val replyTo = TopicPath(responsePrefix + reqId)

      logger.trace(s"Request and response model via:${topic.toString} replyTo: ${replyTo.toString}")

      waitingBoard(reqId) = Waiting(promise, topic.toString, System.currentTimeMillis(), timeout.duration.toMillis)
      smqd.publish(topic, ResponsibleMessage(replyTo, msg))

    // response message from responder
    case (topicPath: TopicPath, response) =>
      val reqIdStr = topicPath.tokens.last.name
      val reqId = reqIdStr.toLong

      waitingBoard.remove(reqId) match {
        case Some(waiting) =>
          response match {
            case ex: Throwable =>
              waiting.promise.failure(ex)
            case _ =>
              waiting.promise.success(response)
          }
        case _ =>
          logger.warn("Missing waiting board for reqId: {}", reqId)
      }

    // cleaning waiting board
    case Check =>
      val cur = System.currentTimeMillis()
      waitingBoard.filter{ case (reqId, w) => cur - w.requestTime > w.timeout }.keys.foreach{ reqId =>
        waitingBoard.remove(reqId) match {
          case Some(w) =>
            logger.warn("Waiting board timeout: reqId({}), dest: {}", reqId, w.dest)
            w.promise.failure(new java.util.concurrent.TimeoutException(s"Request($reqId) get no response from ${w.dest}"))
          case None =>
            logger.warn("Waiting board timeout: {} missing entry (strange behavior)", reqId)
        }
      }
  }
}
