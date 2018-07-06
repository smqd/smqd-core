package com.thing2x.smqd.rest.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd._
import com.thing2x.smqd.rest.RestController
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import spray.json._

import scala.collection.immutable.SortedSet


/**
  * 2018. 7. 6. - Created by Kwon, Yeong Eon
  */
class ClientController(name: String, smqd: Smqd, config: Config) extends RestController(name, smqd, config) with Directives with StrictLogging  {
  override def routes: Route = clients

  private def clients: Route = {
    ignoreTrailingSlash {
      parameters('curr_page.as[Int].?, 'page_size.as[Int].?) { (pCurrPage, pPageSize) =>
        val currPage = pCurrPage.getOrElse(1)
        val pageSize = pPageSize.getOrElse(20)

        path(Remaining) { clientId =>
          get { getClients(Some(clientId), currPage, pageSize) }
        } ~
        pathEnd {
          get { getClients(None, currPage, pageSize) }
        }
      }
    }
  }

  private def getClients(clientId: Option[String], pCurrPage: Int, pPageSize: Int): Route = {
    val result = {
      val rt = smqd.snapshotRegistrations

      val filtered = clientId match {
        case Some(cid) =>
          SortedSet[Registration]() ++
            rt.filter(r => r.clientId.isDefined && r.clientId.get.id.contains(cid))
        case None =>
          SortedSet[Registration]() ++ rt
      }
      val totalNum = filtered.size
      val totalPage = (totalNum + pPageSize - 1)/pPageSize
      val currPage = math.max(math.min(pCurrPage, totalPage), 1)
      val pageSize = math.max(math.min(pPageSize, 100), 1)

      val from = (currPage - 1) * pageSize
      val until = from + pageSize

      val sliced = filtered.slice(from, until).toSeq

      JsObject(
        "current_page" -> JsNumber(currPage),
        "page_size" -> JsNumber(pageSize),
        "total_num" -> JsNumber(totalNum),
        "total_page" -> JsNumber(totalPage),
        "objects" -> sliced.toJson
      )
    }


    complete(StatusCodes.OK, restSuccess(0, result))
  }
}
