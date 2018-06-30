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

package com.thing2x.smqd.rest

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import spray.json._
import com.thing2x.smqd.{Smqd, TopicPath}

/**
  * 2018. 6. 21. - Created by Kwon, Yeong Eon
  */
class RoutesController(name: String, smqd: Smqd, config: Config) extends RestController(name, smqd, config) with Directives with StrictLogging {
  override def routes: Route = routes0

  def routes0: Route = {
    ignoreTrailingSlash {
      get {
        path(Remaining) { topicStr =>
          // GET api/v2/routes/{topic}
          val topicPath = TopicPath(topicStr)
          val result = smqd.snapshotRoutes.filter( _._1.matchFor(topicPath) )

          if (result.isEmpty) {
            complete(StatusCodes.NotFound, restError(404, s"Not Found - $topicStr"))
          }
          else {
            val json = result.map{ case (topicName, rs) => JsObject(
              "topic" -> JsString(topicName.toString),
              "node" -> rs.map(r => r.actor.path.address.hostPort).toJson
            )}.toJson
            complete(StatusCodes.OK, restSuccess(0, json))
          }
        } ~
        pathEnd {
          parameters('curr_page.as[Int].?, 'page_size.as[Int].?) { (pCurrPage, pPageSize) =>
            // GET api/v1/routes?curr_page={page_no}&page_size={page_size}

            var currPage = pCurrPage.getOrElse(1)
            var pageSize = pPageSize.getOrElse(20)

            val result = smqd.snapshotRoutes
            val totalNum = result.size
            val totalPage = (totalNum + pageSize + 1)/pageSize

            currPage = math.max(math.min(currPage, totalPage), 1)
            pageSize = math.max(math.min(pageSize, 100), 1)

            val from = (currPage - 1) * pageSize
            val until = from + pageSize

            val sliced = result.slice(from, until)

            val json = JsObject(
              "current_page" -> JsNumber(currPage),
              "page_size" -> JsNumber(pageSize),
              "total_num" -> JsNumber(totalNum),
              "total_page" -> JsNumber(totalPage),
              "objects" -> sliced.map{case (topic, rs) => JsObject(
                "topic" -> JsString(topic.toString),
                "node" -> rs.map(r => r.actor.path.address.hostPort).toJson
              )}.toJson
            )

            complete(StatusCodes.OK, restSuccess(0, json))
          }
        }
      }
    }
  }
}
