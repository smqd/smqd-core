package t2x.smqd.protocol

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.ChiefActor.{Ready, ReadyAck}
import t2x.smqd._
import t2x.smqd.util.ClassLoading

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
object ProtocolNotificationManager {
  val actorName: String = "protocols"
  val topic: TopicPath = TPath.parseForTopic("$SYS/protocols").get
}

import ProtocolNotificationManager._

class ProtocolNotificationManager(smqd: Smqd) extends Actor with ClassLoading with StrictLogging {

  override def preStart(): Unit = {
  }

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case m: ProtocolNotification =>
      smqd.publish(topic, m)
    case m: Any =>
      logger.warn(s"unhandled message: $m")
  }
}



