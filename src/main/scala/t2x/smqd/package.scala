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

package t2x

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.OverflowStrategy
import com.codahale.metrics.Counter
import com.typesafe.config.Config
import spray.json._
import t2x.smqd.Smqd.NodeInfo
import t2x.smqd.rest.jsonFormat6

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.language.implicitConversions

/**
  * 2018. 6. 24. - Created by Kwon, Yeong Eon
  */
package object smqd extends DefaultJsonProtocol {
  implicit def stringToFilterPath(str: String): FilterPath = FilterPath(str)
  implicit def stringToTopicPath(str: String): TopicPath = TopicPath(str)

  class OptionalConfig(base: Config) {
    def getOptionInt(path: String): Option[Int] =
      if (base.hasPath(path)) {
        Some(base.getInt(path))
      } else {
        None
      }

    def getOptionString(path: String): Option[String] =
      if (base.hasPath(path)) {
        Some(base.getString(path))
      } else {
        None
      }

    def getOptionConfig(path: String): Option[Config] =
      if (base.hasPath(path)) {
        Some(base.getConfig(path))
      } else {
        None
      }

    def getOptionDuration(path: String): Option[FiniteDuration] =
      if (base.hasPath(path)) {
        Some(FiniteDuration(base.getDuration(path).toMillis, MILLISECONDS))
      } else {
        None
      }

    def getOverflowStrategy(path: String): OverflowStrategy =
      getOptionString(path).getOrElse("drop-new").toLowerCase match {
        case "drop-head"    => OverflowStrategy.dropHead
        case "drop-tail"    => OverflowStrategy.dropTail
        case "drop-buffer"  => OverflowStrategy.dropBuffer
        case "backpressure" => OverflowStrategy.backpressure
        case "drop-new"     => OverflowStrategy.dropNew
        case "fail"         => OverflowStrategy.fail
        case _              => OverflowStrategy.dropNew
      }
  }

  implicit def configToOptionalConfig(base: Config): OptionalConfig = new OptionalConfig(base)

  implicit val nodeInfoFormat: RootJsonFormat[NodeInfo] = jsonFormat6(NodeInfo)

  implicit object MetricCounterFormat extends RootJsonFormat[Counter] {
    override def write(c: Counter): JsValue = JsObject("count" -> JsNumber(c.getCount))
    override def read(json: JsValue): Counter = ???
  }

  implicit object RouteFormat extends RootJsonFormat[t2x.smqd.SmqdRoute] {
    override def read(json: JsValue): SmqdRoute = ???
    override def write(rt: SmqdRoute): JsValue = JsObject(
      "topic" -> JsString(rt.filterPath.toString),
      "node" -> JsString(rt.actor.path.toString))
  }
}
