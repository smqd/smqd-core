package t2x.smqd

import akka.actor.{Actor, Props}
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberLeft, UnreachableMember}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.delivery.DeliveryManagerActor
import t2x.smqd.fault.FaultNotificationManager
import t2x.smqd.protocol.ProtocolNotificationManager
import t2x.smqd.replica.{ClusterModeReplicationActor, LocalModeReplicationActor, ReplicationActor}
import t2x.smqd.session.SessionManagerActor

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

import t2x.smqd.ChiefActor._

class ChiefActor(smqd: Smqd, registry: Registry, router: Router, retainer: Retainer)
  extends Actor with StrictLogging {

  override def preStart(): Unit = {
    context.actorOf(Props(classOf[SessionManagerActor], smqd), SessionManagerActor.actorName)
    context.actorOf(Props(classOf[FaultNotificationManager], smqd), FaultNotificationManager.actorName)
    context.actorOf(Props(classOf[ProtocolNotificationManager], smqd), ProtocolNotificationManager.actorName)
    context.actorOf(Props(classOf[DeliveryManagerActor]), DeliveryManagerActor.actorName)
    context.actorOf(Props(classOf[RegistryCallbackManagerActor]), RegistryCallbackManagerActor.actorName)
    context.actorOf(Props(classOf[RequestManagerActor], smqd), RequestManagerActor.actorName)

    smqd.cluster match {
      case Some(_) =>
        val localRouter = context.actorOf(Props(classOf[ClusterAwareLocalRouter], registry), ClusterAwareLocalRouter.actorName)
        context.actorOf(Props(classOf[ClusterModeReplicationActor], router, retainer, localRouter), ReplicationActor.actorName)
      case _ =>
        context.actorOf(Props(classOf[LocalModeReplicationActor]), ReplicationActor.actorName)
    }

    context.children.foreach{ child =>
      try {
        implicit val readyTimeout: Timeout = 1 second
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
  }
}
