package t2x.smqd.fault

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.ChiefActor.ReadyAck
import t2x.smqd._
import t2x.smqd.util.ClassLoading

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */

object FaultNotificationManager {
  val actorName: String = "faults"
  val topic: TopicPath = TPath.parseForTopic("$SYS/faults").get
}

import t2x.smqd.fault.FaultNotificationManager._

class FaultNotificationManager(smqd: Smqd) extends Actor with ClassLoading with StrictLogging {

  override def preStart(): Unit = {
  }

  override def receive: Receive = {
    case ChiefActor.Ready =>
      context.become(receive0)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case ft: Fault =>
      // publish fault message
      smqd.publish(topic, ft)
  }
}
