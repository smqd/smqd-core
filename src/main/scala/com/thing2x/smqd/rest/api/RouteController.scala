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
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.{FilterPath, Smqd, SmqdRoute, TopicPath}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import spray.json._

/**
  * 2018. 6. 21. - Created by Kwon, Yeong Eon
  */
class RouteController(name: String, smqd: Smqd, config: Config) extends RestController(name, smqd, config) with Directives with StrictLogging {
  override def routes: Route = routes0

  private def routes0: Route = {
    ignoreTrailingSlash {
      get {
        path(Remaining) { topicStr =>
          // GET api/v1/routes/{topic}
          val topicPath = TopicPath(topicStr)
          val result = smqd.snapshotRoutes.filter( _._1.matchFor(topicPath) )

          if (result.isEmpty) {
            complete(StatusCodes.NotFound, restError(404, s"Not Found - $topicStr"))
          }
          else {
            val json = result.map{ case (topicName, rs) => JsObject(
              "topic" -> JsString(topicName.toString),
              "nodes" -> rs.map(r => r.actor.path.address.hostPort).toJson
            )}.toJson
            complete(StatusCodes.OK, restSuccess(0, json))
          }
        } ~
        pathEnd {
          parameters('curr_page.as[Int].?, 'page_size.as[Int].?) { (currPage, pageSize) =>
            // GET api/v1/routes?curr_page={page_no}&page_size={page_size}

            implicit object RouteFormat extends RootJsonFormat[(FilterPath, Set[SmqdRoute])] {
              def read(json: JsValue): (FilterPath, Set[SmqdRoute]) = ???
              def write(r: (FilterPath, Set[SmqdRoute])): JsValue = {
                val topic = r._1; val routeSet = r._2
                JsObject (
                  "topic" -> JsString(topic.toString),
                  "nodes" -> routeSet.map(elm => elm.nodeName).toJson
                )
              }
            }

            val result = smqd.snapshotRoutes
            complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
          }
        }
      }
    }
  }
}
