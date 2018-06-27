package t2x.smqd.net.http

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.RouteResult.Complete
import com.typesafe.scalalogging.Logger

/**
  * 2018. 6. 21. - Created by Kwon, Yeong Eon
  */
class HttpServiceLogger(logger: Logger, name: String = "-") extends LoggingAdapter {

  override def isErrorEnabled: Boolean = true

  override def isWarningEnabled: Boolean = true

  override def isInfoEnabled: Boolean = true

  override def isDebugEnabled: Boolean = true

  override protected def notifyError(message: String): Unit = {
    logger.error("[{}] {}", name, message)
  }

  override protected def notifyError(cause: Throwable, message: String): Unit = {
    logger.error("[{}] {}", name, message, cause)
  }

  override protected def notifyWarning(message: String): Unit = {
    logger.warn("[{}] {}", name, message)
  }

  override protected def notifyInfo(message: String): Unit = {
    logger.info("[{}] {}", name, message)
  }

  override protected def notifyDebug(message: String): Unit = {
    logger.debug("---{}", message)
  }

  def accessLog(requestTime: Long)(req: HttpRequest)(rsp: Any): Unit = {
    val path = req.uri.rawQueryString match {
      case Some(str) => req.getUri.path+"?"+str
      case _ => req.getUri.path
    }

    val time = (System.nanoTime - requestTime).toDouble / 1000000.0

    val result = rsp match {
      case Complete(rsp: HttpResponse) =>
        s";${rsp.status.intValue} ${rsp.status.reason}"
      case m =>
        s"-${m.toString}"
    }

    info(f"${req.method.name} $path $time%.3f ms $result")
  }
}

