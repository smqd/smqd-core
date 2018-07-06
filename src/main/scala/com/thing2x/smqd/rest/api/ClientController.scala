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
      parameters('curr_page.as[Int].?, 'page_size.as[Int].?) { (currPage, pageSize) =>
        path(Remaining) { clientId =>
          get { getClients(Some(clientId), currPage, pageSize) }
        } ~
        pathEnd {
          get { getClients(None, currPage, pageSize) }
        }
      }
    }
  }

  private def getClients(clientId: Option[String], currPage: Option[Int], pageSize: Option[Int]): Route = {
    val result = {
      val rt = smqd.snapshotRegistrations

      val filtered = clientId match {
        case Some(cid) =>
          SortedSet[Registration]() ++
            rt.filter(r => r.clientId.isDefined && r.clientId.get.id.contains(cid))
        case None =>
          SortedSet[Registration]() ++ rt
      }

      pagenate(filtered, currPage, pageSize)
    }

    complete(StatusCodes.OK, restSuccess(0, result))
  }
}
