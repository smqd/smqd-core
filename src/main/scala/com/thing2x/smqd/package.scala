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

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.pattern.after
import akka.stream.OverflowStrategy
import akka.util.Timeout
import com.codahale.metrics.Counter
import com.typesafe.config._
import spray.json._

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

// 2018. 6. 24. - Created by Kwon, Yeong Eon

/**
  *
  */
package object smqd extends DefaultJsonProtocol {
  implicit def stringToFilterPath(str: String): FilterPath = FilterPath(str)
  implicit def stringToTopicPath(str: String): TopicPath = TopicPath(str)

  class OptionalConfig(base: Config) {
    def getOptionBoolean(path: String): Option[Boolean] =
      if (base.hasPath(path))
        Some(base.getBoolean(path))
      else
        None

    def getOptionLong(path: String): Option[Long] =
      if (base.hasPath(path))
        Some(base.getLong(path))
      else
        None

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

    def getOptionStringList(path: String): Option[java.util.List[String]] =
      if (base.hasPath(path)) {
        Some(base.getStringList(path))
      }
      else {
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

  implicit class FutureWithTimeout[T](f: Future[T]) {
    def withTimeout(exception: => Throwable)(implicit timeout: Timeout, system: ActorSystem, ec: ExecutionContext): Future[T] = {
      Future.firstCompletedOf(Seq(f, after(timeout.duration, system.scheduler)(Future.failed(exception))))
    }
  }

  /**
    *
    * @param nodeName
    * @param api
    * @param address node's address that has format as "system@ipaddress:port"
    * @param status  membership status
    * @param roles   list of roles
    * @param dataCenter data center name
    * @param isLeader true if the node is leader of the cluster
    */
  case class NodeInfo(nodeName: String, api: Option[EndpointInfo], address: String, status: String, roles: Set[String], dataCenter: String, isLeader: Boolean)

  case class EndpointInfo(address: Option[String], secureAddress: Option[String])

  implicit object TypesafeConfigFormat extends RootJsonFormat[Config] {
    private val ParseOptions = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)
    private val RenderOptions = ConfigRenderOptions.concise().setJson(true)
    override def read(json: JsValue): Config = json match {
      case obj: JsObject => ConfigFactory.parseString(obj.compactPrint, ParseOptions)
      // case _ => deserializationError("Expected JsObject for Config deserialization")
      case _ =>
        throw new ParseException("json parse error: unsable to build config object", 0)
    }
    override def write(config: Config): JsValue = {
      //      val entries = config.entrySet().asScala.map { entry =>
      //        val key = entry.getKey
      //        val value: com.typesafe.config.ConfigValue = entry.getValue
      //        val opt = value.render(ConfigRenderOptions.concise())
      //        key ->JsonParser(opt)
      //      }.toMap
      JsonParser(config.root.render(RenderOptions))
    }
  }

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
