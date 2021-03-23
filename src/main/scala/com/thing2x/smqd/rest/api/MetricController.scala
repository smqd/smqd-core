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
import com.codahale.metrics.{Counter, Metric, SharedMetricRegistries}
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.util.FailFastCirceSupport._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.syntax._
import scala.jdk.CollectionConverters._

// 2018. 6. 21. - Created by Kwon, Yeong Eon

object MetricController {

  /*
  implicit object MetricCounterFormat extends RootJsonFormat[Counter] {
    override def write(c: Counter): JsValue = JsObject("count" -> JsNumber(c.getCount))
    override def read(json: JsValue): Counter = ???
  }
   */
}

class MetricController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging {
  override def routes: Route = context.oauth2.authorized { _ => metrics }

  def metrics: Route = {
    ignoreTrailingSlash {
      path(Remaining.?) { prefix =>
        get {
          complete(StatusCodes.OK, restSuccess(0, report(prefix)))
        }
      }
    }
  }

  private def report(prefixOpt: Option[String]): Json = {

    val registry = SharedMetricRegistries.getDefault
    var prefixLen = 0

    def prefixNormalize(str: String): String = {
      val prefixNorm = str.replaceAll("/", ".")
      if (prefixNorm.endsWith(".")) prefixNorm else prefixNorm + "."
    }

    ////////////////////////////////
    // Counters
    val counters = prefixOpt match {
      case Some(prefixStr) if prefixStr.length > 0 =>
        val prefix = prefixNormalize(prefixStr)
        prefixLen = prefix.length
        registry.getCounters((name: String, _: Metric) => name.startsWith(prefix)).asScala
      case _ =>
        registry.getCounters.asScala
    }

    val result = counters.map { case (key: String, counter: Counter) => (key.substring(prefixLen), counter.getCount) }
    var merged: Map[String, Json] = result.map { case (key, num) => (key, Json.fromLong(num)) }.toMap

    ////////////////////////////////
    // Gauges
    val gauges = prefixOpt match {
      case Some(prefixStr) if prefixStr.length > 0 =>
        val prefix = prefixNormalize(prefixStr)
        prefixLen = prefix.length
        registry.getGauges((name: String, _: Metric) => name.startsWith(prefix)).asScala
      case _ =>
        registry.getGauges.asScala
    }

    merged ++= gauges.map { case (key: String, gauge) =>
      gauge.getValue match {
        case n: Int    => (key.substring(prefixLen), Json.fromInt(n))
        case n: Long   => (key.substring(prefixLen), Json.fromLong(n))
        case n: Float  => (key.substring(prefixLen), Json.fromFloat(n).getOrElse(Json.fromInt(-1)))
        case n: Double => (key.substring(prefixLen), Json.fromDouble(n).getOrElse(Json.fromInt(-1)))
      }
    }

    prefixOpt match {
      case Some(prefixStr) if prefixStr.length > 0 =>
        val prefixNorm = prefixStr.replaceAll("/", ".")
        Json.obj((prefixNorm, merged.asJson))
      case _ =>
        merged.asJson
    }
  }
}
