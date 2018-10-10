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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd.net.http.HttpServiceContext
import com.typesafe.scalalogging.StrictLogging

// 10/9/18 - Created by Kwon, Yeong Eon

/**
  *
  */
class ResourceController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging {

  private val resourceRegex = """(.+\.[\w]{1,6}$)""".r
  private def withTrailingSlash(path: String): String = if (path endsWith "/") path else path + '/'

  private val prefix = context.config.getString("prefix")
  private val basedir = context.config.getString("resource")

  val routes: Route = dashboard ~ doorman

  def dashboard: Route =
    path("dashboard") {
      pathEndOrSingleSlash {
        redirect("/dashboard/index.html", StatusCodes.PermanentRedirect)
      }
    } ~
    path("dashboard" / Remaining) {
      case path @ resourceRegex(_) =>
        getFromFile(withTrailingSlash(basedir) + path)
      case _ =>
        getFromFile(withTrailingSlash(basedir) + "index.html")
    }

  private val dashboardEntrance: Route = redirect("/dashboard/index.html", StatusCodes.PermanentRedirect)

  def doorman: Route =
    path("") {
      dashboardEntrance
    } ~
    path("index.html") {
      dashboardEntrance
    } ~
    path("favicon.ico") {
      getFromFile(withTrailingSlash(basedir)+"favicon.ico")
    }

}
