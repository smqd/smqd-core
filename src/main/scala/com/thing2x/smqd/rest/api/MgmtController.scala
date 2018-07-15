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
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import com.thing2x.smqd._
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.typesafe.scalalogging.StrictLogging
import spray.json._

// 2018. 6. 20. - Created by Kwon, Yeong Eon

class MgmtController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging {

  val routes: Route = context.oauth2.authorized { _ => version ~ nodes }

  private def version: Route = {
    val smqdInstance = context.smqdInstance
    path("version") {
      get {
        parameters('fmt.?) {
          case Some("version") =>
            complete(StatusCodes.OK, restSuccess(0, JsObject("version" -> JsString(smqdInstance.version))))
          case Some("commit") =>
            complete(StatusCodes.OK, restSuccess(0, JsObject("commitVersion" -> JsString(smqdInstance.commitVersion))))
          case _ =>
            val os = smqdInstance.javaOperatingSystem
            val osjs = JsString(s"${os.name}/${os.version}/${os.arch} (${os.processors} cores)")

            complete(StatusCodes.OK, restSuccess(0,
              JsObject(
                "version" -> JsString(smqdInstance.version),
                "commitVersion" -> JsString(smqdInstance.commitVersion),
                "nodename" -> JsString(smqdInstance.nodeName),
                "jvm" -> JsString(smqdInstance.javaVersionString),
                "os" -> osjs
              )))
        }
      }
    }
  }

  private def nodes: Route = {
    ignoreTrailingSlash {
      path("nodes") {
        get { getNodes(None)  }
      } ~
      path("nodes" / Segment.?) { nodeName =>
        get { getNodes(nodeName) }
      }
    }
  }

  private def getNodes(nodeName: Option[String]): Route = {
    val smqdInstance = context.smqdInstance
    import smqdInstance.Implicit._
    implicit val apiInfoFormat: RootJsonFormat[EndpointInfo] = jsonFormat2(EndpointInfo)
    implicit val nodeInfoFormat: RootJsonFormat[NodeInfo] = jsonFormat7(NodeInfo)
    nodeName match {
      case Some(node) =>
        val jsval = for {
          result <- smqdInstance.node(node)
          resonse = restSuccess(0, result.toJson)
        } yield resonse
        complete(StatusCodes.OK, jsval)
      case None =>
        val jsval = for {
          result <- smqdInstance.nodes
          resonse = restSuccess(0, result.toJson)
        } yield resonse
        complete(StatusCodes.OK, jsval)
    }
  }
}
