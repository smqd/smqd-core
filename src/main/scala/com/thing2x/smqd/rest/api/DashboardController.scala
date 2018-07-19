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
import com.typesafe.scalalogging.StrictLogging

// 2018. 6. 20. - Created by Kwon, Yeong Eon

class DashboardController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging {
  private val resourceRegex = """(.+\.[\w]{1,6}$)""".r

  val routes: Route =
    path("dashboard") {
      pathEndOrSingleSlash {
        getFromResource("dashboard/index.html")
      }
    } ~
    path("dashboard/index.html") {
      redirect("/dashboard/", StatusCodes.PermanentRedirect)
    } ~
    path("dashboard" / Remaining) {
      case path @ resourceRegex(_) =>
        getFromResource("dashboard/" + path)
      case _ =>
        redirect("/dashboard/", StatusCodes.PermanentRedirect)
    }
}
