package t2x.smqd.session

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.ChiefActor.ReadyAck
import t2x.smqd.session.SessionManagerActor._
import t2x.smqd._

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
