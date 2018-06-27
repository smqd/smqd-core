package t2x.smqd.impl

import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.fault.BadUserNameOrPassword
import t2x.smqd.{AuthDelegate, SmqResult, SmqSuccess}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
class DefaultAuthDelegate extends AuthDelegate with StrictLogging {

  override def authenticate(clientId: String, userName: Option[String], password: Option[Array[Byte]]): Future[SmqResult] = {

    Future {
      logger.debug(s"[$clientId] userName: $userName password: $password")

      if (userName.isDefined && password.isDefined) {
        if (userName.get == new String(password.get, "utf-8"))
          SmqSuccess
        else
          BadUserNameOrPassword(clientId, "Bad user name or password ")
      }
      else {
        SmqSuccess
      }
    }

  }
}
