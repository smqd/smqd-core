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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd.SessionStore.SubscriptionData
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.net.http.OAuth2.OAuth2Claim
import com.thing2x.smqd.rest.RestController
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import com.thing2x.smqd.util.FailFastCirceSupport._

import scala.util.{Failure, Success}

// 2018. 7. 6. - Created by Kwon, Yeong Eon

class ClientController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging {
  override def routes: Route = context.oauth2.authorized { claim => clients(claim) }

  private def clients(claim: OAuth2Claim): Route = {
    ignoreTrailingSlash {
      get {
        parameters("curr_page".as[Int].?, "page_size".as[Int].?, "query".as[String].?) { (currPage, pageSize, searchName) =>
          path(Segment.?) { clientId =>
            getClients(clientId, searchName, currPage, pageSize)
          }
        }
      }
    }
  }

  private def clientSubscriptionToJson(subscriptions: Seq[SubscriptionData]): Json =
    Json.arr(subscriptions.map { s =>
      Json.obj(
        ("topic", Json.fromString(s.filterPath.toString)),
        ("qos", Json.fromLong(s.qos.id))
      )
    }: _*)

  private def clientInfoToJson(cid: String, channelId: String, subscriptions: Seq[SubscriptionData], pendingMessages: Long): Json =
    Json.obj(
      ("clientId", Json.fromString(cid)),
      ("channelId", Json.fromString(channelId)),
      ("pendingMessages", Json.fromLong(pendingMessages)),
      ("subscriptions", clientSubscriptionToJson(subscriptions))
    )

  private def getClientExactMatch(clientId: String): Route = {
    import context.smqdInstance.Implicit._
    val f = context.smqdInstance.snapshotSessions(None)
    val jsResult = f.map { list =>
      val filtered = list.filter(_.clientId.id == clientId)
      if (filtered.nonEmpty) { // may find multiple registrations of a client
        val clientId = filtered.head.clientId
        val subscriptions = filtered.head.subscriptions
        val pendings = filtered.head.pendingMessageSize

        restSuccess(0, clientInfoToJson(clientId.id, clientId.channelId.getOrElse(""), subscriptions, pendings))
      } else {
        restError(404, s"Not found: $clientId")
      }
    }
    complete(StatusCodes.OK, jsResult)
  }

  private def getClientsWithClientId(search: String, currPage: Option[Int], pageSize: Option[Int]): Route = {
    import context.smqdInstance.Implicit._
    val f = context.smqdInstance.snapshotSessions(None)
    val jsResult = f.map { list =>
      val filtered = list
        .filter(_.clientId.id.contains(search))
        .sortWith { case (lk, rk) => lk.clientId.id.compare(rk.clientId.id) < 0 }
        .map { data =>
          clientInfoToJson(data.clientId.id, data.clientId.channelId.getOrElse(""), data.subscriptions, data.pendingMessageSize)
        }
      restSuccess(0, pagenate(filtered, currPage, pageSize))
    }
    complete(StatusCodes.OK, jsResult)
  }

  private def getClients(currPage: Option[Int], pageSize: Option[Int]): Route = {
    import context.smqdInstance.Implicit._
    val f = context.smqdInstance.snapshotSessions(None)
    val jsResult = f.map { list =>
      val filtered = list
        .sortWith { case (lk, rk) => lk.clientId.id.compare(rk.clientId.id) < 0 }
        .map { data =>
          clientInfoToJson(data.clientId.id, data.clientId.channelId.getOrElse(""), data.subscriptions, data.pendingMessageSize)
        }
      restSuccess(0, pagenate(filtered, currPage, pageSize))
    }
    complete(StatusCodes.OK, jsResult)
  }

  private def getClients(clientId: Option[String], searchName: Option[String], currPage: Option[Int], pageSize: Option[Int]): Route = {
    clientId match {
      case Some(cid) => // exact match
        getClientExactMatch(cid)
      case None => // search
        searchName match {
          case Some(search) => // query
            getClientsWithClientId(search, currPage, pageSize)
          case None => // all
            getClients(currPage, pageSize)
        }
    }
  }
}
