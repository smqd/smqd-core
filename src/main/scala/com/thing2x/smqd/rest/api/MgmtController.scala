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
import com.thing2x.smqd.rest.RestController
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import spray.json._

/**
  * 2018. 6. 20. - Created by Kwon, Yeong Eon
  */
class MgmtController(name: String, smqd: Smqd, config: Config) extends RestController(name, smqd, config) with Directives with StrictLogging {

  val routes: Route = version ~ nodes

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
                "version" -> JsString(smqd.version),
                "commitVersion" -> JsString(smqd.commitVersion),
                "nodename" -> JsString(smqd.nodeName),
                "jvm" -> JsString(smqd.javaVersionString))))
        }
      }
    }
  }

  def nodes: Route = {
    ignoreTrailingSlash {
      path("nodes") {
        get { getNodes(None)  }
      } ~
      path("nodes" / Remaining.?) { nodeName =>
        get { getNodes(nodeName) }
      }
    }
  }

  private def getNodes(nodeName: Option[String]): Route = {
    import smqd.Implicit._
    implicit val nodeInfoFormat: RootJsonFormat[NodeInfo] = jsonFormat7(NodeInfo)
    nodeName match {
      case Some(node) =>
        val jsval = for {
          result <- smqd.node(node)
          resonse = restSuccess(0, result.toJson)
        } yield resonse
        complete(StatusCodes.OK, jsval)
      case None =>
        val jsval = for {
          result <- smqd.nodes
          resonse = restSuccess(0, result.toJson)
        } yield resonse
        complete(StatusCodes.OK, jsval)
    }
  }
}
