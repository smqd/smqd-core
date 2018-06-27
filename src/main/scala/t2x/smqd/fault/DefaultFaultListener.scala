package t2x.smqd.fault

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.{FilterPath, Service, Smqd, TopicPath}

/**
  * 2018. 6. 19. - Created by Kwon, Yeong Eon
  */
class DefaultFaultListener(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  override def start(): Unit = {
    val topic = config.getString("subscribe.topic")
    smqd.subscribe(FilterPath(topic)) {
      case (topicPath, msg) => onFault(topicPath, msg)
    }
  }

  override def stop(): Unit = {

  }

  def onFault(topic: TopicPath, msg: Any): Unit = {
    if (!msg.isInstanceOf[Fault]) return
    val ft = msg.asInstanceOf[Fault]

    ft match {
      case f: SessionFault =>
        logger.warn(s"[${f.sessionId}] FAULT: ${f.message}")

      case ServerUnavailable =>
        logger.warn(s"FAULT: Auth Failed: $ft")

      case f: Fault =>
        logger.warn(s"FAULT: ${f.getClass.getSimpleName}")

      case _ =>
        logger.warn(s"FAULT: unknown fault: $ft")
    }
  }
}
