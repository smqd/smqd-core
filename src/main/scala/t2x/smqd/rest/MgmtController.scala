package t2x.smqd.rest

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import spray.json._
import t2x.smqd._

/**
  * 2018. 6. 20. - Created by Kwon, Yeong Eon
  */
class MgmtController(name: String, smqd: Smqd, config: Config) extends RestController(name, smqd, config) with Directives with StrictLogging {

  val routes: Route = version ~ nodes ~ node

  def version: Route = {
    path("version") {
      get {
        parameters('fmt.?) {
          case Some("version") =>
            complete(StatusCodes.OK, restSuccess(0, JsObject("version" -> JsString(smqd.version))))
          case Some("commit") =>
            complete(StatusCodes.OK, restSuccess(0, JsObject("commitVersion" -> JsString(smqd.commitVersion))))
          case _ =>
            complete(StatusCodes.OK, restSuccess(0,
              JsObject(
                "version"-> JsString(smqd.version),
                "commitVersion" -> JsString(smqd.commitVersion),
                "nodename"-> JsString(smqd.nodeName))))
        }
      }
    }
  }

  def nodes: Route = {
    ignoreTrailingSlash {
      path("nodes") {
        get {
          complete(StatusCodes.OK, restSuccess(0, smqd.nodes.toJson))
        }
      }
    }
  }

  def node: Route = {
    ignoreTrailingSlash {
      path("nodes" / Remaining.?) { nodeName =>
        get {
          nodeName match {
            case Some(addr) =>
              val found = smqd.nodes.filter(_.address == addr)
              if (found.size == 1)
                complete(StatusCodes.OK, restSuccess(0, found.head.toJson))
              else if (found.isEmpty)
                complete(StatusCodes.NotFound, restError(404, s"node not found: $addr"))
              else
                complete(StatusCodes.PreconditionFailed, restError(412, s"multiple nodes found: $addr"))
            case None =>
              complete(StatusCodes.Gone, restError(410, s"no node address is specified"))
          }
        }
      }
    }
  }
}
