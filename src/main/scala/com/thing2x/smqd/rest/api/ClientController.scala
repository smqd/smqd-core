// Copyright 2018 UANGEL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.thing2x.smqd.rest.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd.SessionStore.SubscriptionData
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.net.http.OAuth2.OAuth2Claim
import com.thing2x.smqd.rest.RestController
import com.typesafe.scalalogging.StrictLogging
import spray.json._

// 2018. 7. 6. - Created by Kwon, Yeong Eon

class ClientController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging  {
  override def routes: Route = context.oauth2.authorized{ claim => clients(claim) }

  private def clients(claim: OAuth2Claim): Route = {
    ignoreTrailingSlash {
      get {
        parameters('curr_page.as[Int].?, 'page_size.as[Int].?, 'query.as[String].?) { (currPage, pageSize, searchName) =>
          path(Segment.?) { clientId =>
            getClients(clientId, searchName, currPage, pageSize)
          }
        }
      }
    }
  }

  private def getClients(clientId: Option[String], searchName: Option[String], currPage: Option[Int], pageSize: Option[Int]): Route = {

    def clientSubscriptionToJson(subscriptions:Seq[SubscriptionData]): JsArray = {
      JsArray(
        subscriptions.map{ s => JsObject(
          "topic" -> JsString(s.filterPath.toString),
          "qos" -> JsNumber(s.qos.id))
        }.toVector)
    }

    val list = context.smqdInstance.snapshotSessions

    clientId match {
      case Some(cid) => // exact match
        val filtered = list.filter{ case (k, _) => k.id == cid }
        if (filtered.nonEmpty) { // may find multiple registrations of a client
          val clientId = filtered.head._1
          val subscriptions = filtered.head._2

          complete(StatusCodes.OK, restSuccess(0, JsObject(
            "clientId" -> JsString(clientId.id),
            "channelId" -> JsString(clientId.channelId.getOrElse("")),
            "subscriptions" -> clientSubscriptionToJson(subscriptions))))
        }
        else { // or not found
          complete(StatusCodes.NotFound, restError(404, s"Client not found: $cid"))
        }
      case None => // search
        val result = searchName match {
          case Some(search) => // query
            list.filter{ case (k, _) => k.id.contains(search) }
                .toSeq.sortWith{ case ((lk, _), (rk, _)) => lk.id.compare(rk.id) < 0 }
          case None => // all
            list.toSeq.sortWith{ case ((lk, _), (rk, _)) => lk.id.compare(rk.id) < 0 }
        }
        val jsResult = result.map { case (k, subscriptions) =>
          JsObject(
            "clientId" -> JsString(k.id),
            "channelId" -> JsString(k.channelId.getOrElse("")),
            "subscriptions" -> clientSubscriptionToJson(subscriptions)
          )
        }

        complete(StatusCodes.OK, restSuccess(0, pagenate(jsResult, currPage, pageSize)))
    }
  }
}
