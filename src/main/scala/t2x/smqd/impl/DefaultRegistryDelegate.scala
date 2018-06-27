package t2x.smqd.impl

import com.typesafe.scalalogging.StrictLogging
import t2x.smqd._
import t2x.smqd.session.SessionId

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
/**
  * 2018. 6. 1. - Created by Kwon, Yeong Eon
  */

class DefaultRegistryDelegate extends RegistryDelegate with StrictLogging {
  def allowSubscribe(filterPath: FilterPath, sessionId: SessionId, userName: Option[String]): Future[Boolean] = Future(true)
  def allowPublish(topicPath: TopicPath, sessionId: SessionId, userName: Option[String]): Future[Boolean] = Future(true)
}
