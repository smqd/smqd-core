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

package com.thing2x

import java.text.ParseException

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.codahale.metrics.Counter
import com.typesafe.config._
import spray.json._

import scala.language.implicitConversions

// 2018. 6. 24. - Created by Kwon, Yeong Eon

/**
  *
  */
package object smqd extends DefaultJsonProtocol {
  implicit def stringToFilterPath(str: String): FilterPath = FilterPath(str)
  implicit def stringToTopicPath(str: String): TopicPath = TopicPath(str)

  implicit object MetricCounterFormat extends RootJsonFormat[Counter] {
    override def write(c: Counter): JsValue = JsObject("count" -> JsNumber(c.getCount))
    override def read(json: JsValue): Counter = ???
  }

  implicit object RouteFormat extends RootJsonFormat[com.thing2x.smqd.SmqdRoute] {
    override def read(json: JsValue): SmqdRoute = ???
    override def write(rt: SmqdRoute): JsValue = JsObject(
      "topic" -> JsString(rt.filterPath.toString),
      "node" -> JsString(rt.actor.path.toString))
  }

  implicit object RegistrationFormat extends RootJsonFormat[com.thing2x.smqd.Registration] {
    override def read(json: JsValue): Registration = ???
    override def write(rt: Registration): JsValue = {
      if (rt.clientId.isDefined) {
        val channelId = rt.clientId.get.channelId
        JsObject(
          "topic" -> JsString(rt.filterPath.toString),
          "qos" -> JsNumber(rt.qos.id),
          "clientId" -> JsString(rt.clientId.get.id),
          "channelId" -> JsString(channelId.getOrElse("n/a")))
      }
      else {
        JsObject(
          "topic" -> JsString(rt.filterPath.toString),
          "qos" -> JsNumber(rt.qos.id),
          "actor" -> JsString(rt.actor.path.toString))
      }
    }
  }
}
