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
import com.codahale.metrics.{Counter, Gauge, Metric, SharedMetricRegistries}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.rest.RestController
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import spray.json.{JsObject, _}

import scala.collection.JavaConverters._

// 2018. 6. 21. - Created by Kwon, Yeong Eon

class MetricController(name: String, smqdInstance: Smqd, config: Config) extends RestController(name, smqdInstance, config) with Directives with StrictLogging  {
  override def routes: Route = metrics

  def metrics: Route = {
    ignoreTrailingSlash {
      path(Remaining.?) { prefix =>
        get {
          complete(StatusCodes.OK, restSuccess(0, report(prefix)))
        }
      }
    }
  }

  private def report(prefixOpt: Option[String]): JsValue = {

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
        registry.getCounters( (name: String, _: Metric) => name.startsWith(prefix) ).asScala
      case _ =>
        registry.getCounters.asScala
    }

    val result = counters.map{ case (key: String, counter: Counter) => (key.substring(prefixLen), counter.getCount )}
    var merged: Map[String, JsValue] = result.map{ case(key, num) => (key, JsNumber(num))}.toMap

    ////////////////////////////////
    // Gauges
    val gauges = prefixOpt match {
      case Some(prefixStr) if prefixStr.length > 0 =>
        val prefix = prefixNormalize(prefixStr)
        prefixLen = prefix.length
        registry.getGauges( (name: String, _: Metric) => name.startsWith(prefix)).asScala
      case _ =>
        registry.getGauges.asScala
    }

    merged ++= gauges.map { case (key: String, gauge) =>
        gauge.getValue match {
          case n: Int => (key.substring(prefixLen), JsNumber(n))
          case n: Long => (key.substring(prefixLen), JsNumber(n))
          case n: Double => (key.substring(prefixLen), JsNumber(n))
        }
    }

    prefixOpt match {
      case Some(prefixStr) if prefixStr.length > 0 =>
        val prefixNorm = prefixStr.replaceAll("/", ".")
        JsObject(prefixNorm -> JsObject(merged))
      case _ =>
        JsObject(merged)
    }
  }
}
