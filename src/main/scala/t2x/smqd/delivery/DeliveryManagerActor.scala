package t2x.smqd.delivery

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.ChiefActor.{Ready, ReadyAck}

/**
  * 2018. 6. 8. - Created by Kwon, Yeong Eon
  */
object DeliveryManagerActor {
  val actorName = "delivery"
}

// this actor is in charge delivery publish message in local scope, the incomming messages are routed by Inter Nodes Router
class DeliveryManagerActor extends Actor with StrictLogging {
  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case msg =>
      logger.info("Unhandled Message {}", msg)
  }
}
