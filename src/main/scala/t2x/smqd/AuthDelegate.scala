package t2x.smqd

import scala.concurrent.Future

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
trait AuthDelegate {
  def authenticate(clientId: String, userName: Option[String], password: Option[Array[Byte]]): Future[t2x.smqd.SmqResult]
}
