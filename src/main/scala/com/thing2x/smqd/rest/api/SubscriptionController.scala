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
import com.thing2x.smqd._
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.typesafe.scalalogging.StrictLogging
import spray.json.{RootJsonFormat, _}


// 2018. 7. 12. - Created by Kwon, Yeong Eon

class SubscriptionController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging  {
  override def routes: Route = clients

  private def clients: Route = {
    ignoreTrailingSlash {
      get {
        parameters('curr_page.as[Int].?, 'page_size.as[Int].?, 'query.as[String].?) { (currPage, pageSize, search) =>
          path(Remaining.?) { topic =>
            getSubscriptions(topic, search, currPage, pageSize)
          }
        }
      }
    }
  }

  private def getSubscriptions(topic: Option[String], search: Option[String], currPage: Option[Int], pageSize: Option[Int]): Route = {

    val rt = context.smqdInstance.snapshotRegistrations

    val result = topic match {
      case Some(t) if t.length > 0 => // exact match
        // there might be a bug in akka http to unescape %23 (#) properly, %2B (+) is ok
        val topicPath = t.replaceAll("%23", "#")
        rt.filter(r => r.filterPath.toString == topicPath).groupBy(r => r.filterPath).toSeq
      case _ => // search
        search match {
          case Some(q) => // query
            // there might be a bug in akka http to unescape %23 (#) properly, %2B (+) is ok
            val query = q.replaceAll("%23", "#")
            rt.filter(r => r.filterPath.toString.contains(query)).groupBy(r => r.filterPath).toSeq.sortWith{ case ((lf, _), (rf, _)) => lf.compare(rf) < 0 }
          case None => // all
            rt.groupBy(r => r.filterPath).toSeq.sortWith{ case ((lf, _), (rf, _)) => lf.compare(rf) < 0 }
        }
    }

    implicit object ResultFormat extends RootJsonFormat[(FilterPath, Seq[Registration])] {
      override def read(json: JsValue): (FilterPath, Seq[Registration]) = ???
      override def write(obj: (FilterPath, Seq[Registration])): JsValue = {
        JsObject(
          "topic" -> JsString(obj._1.toString),
          "subscribers" -> JsArray(
            obj._2.map(_.toJson).toVector
          ))
      }
    }

    if (result.nonEmpty) { // may find subscriptions Seq[(FilterPath, Seq[Registration])]
      complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
    }
    else { // or not found
      complete(StatusCodes.NotFound, restError(404, s"Subscription not found: ${topic.getOrElse(search.getOrElse(""))}"))
    }
  }
}
