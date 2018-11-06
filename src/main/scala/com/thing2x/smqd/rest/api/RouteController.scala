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
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.{FilterPath, SmqdRoute, TopicPath}
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Encoder, Json}
import io.circe.syntax._
import com.thing2x.smqd.util.FailFastCirceSupport._

// 2018. 6. 21. - Created by Kwon, Yeong Eon

class RouteController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging {
  override def routes: Route = context.oauth2.authorized{ _ => routes0 }

  private def routes0: Route = {
    ignoreTrailingSlash {
      get {
        path(Remaining) { topicStr =>
          // GET api/v1/routes/{topic}
          val topicPath = TopicPath(topicStr)
          val result = context.smqdInstance.snapshotRoutes.filter( _._1.matchFor(topicPath) )

          if (result.isEmpty) {
            complete(StatusCodes.NotFound, restError(404, s"Not Found - $topicStr"))
          }
          else {
            val json = result.map{ case (topicName, rs) => Json.obj(
              ("topic", Json.fromString(topicName.toString)),
              ("nodes", rs.map(r => r.actor.path.address.hostPort).asJson)
            )}.asJson
            complete(StatusCodes.OK, restSuccess(0, json))
          }
        } ~
        pathEnd {
          parameters('curr_page.as[Int].?, 'page_size.as[Int].?) { (currPage, pageSize) =>
            // GET api/v1/routes?curr_page={page_no}&page_size={page_size}

            implicit val RouteEncoder: Encoder[(FilterPath, Set[SmqdRoute])] = new Encoder[(FilterPath, Set[SmqdRoute])] {
              override def apply(r: (FilterPath, Set[SmqdRoute])): Json = {
                val topic = r._1; val routeSet = r._2
                Json.obj (
                  ("topic", Json.fromString(topic.toString)),
                  ("nodes", routeSet.map(elm => elm.nodeName).asJson)
                )
              }
            }

            val result = context.smqdInstance.snapshotRoutes
            complete(StatusCodes.OK, restSuccess(0, pagenate(result, currPage, pageSize)))
          }
        }
      }
    }
  }
}
