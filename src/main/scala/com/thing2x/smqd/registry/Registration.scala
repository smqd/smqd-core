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

package com.thing2x.smqd.registry

import akka.actor.ActorRef
import com.thing2x.smqd.{ClientId, FilterPath, QoS}
import com.thing2x.smqd.QoS.QoS
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

// 2018. 9. 13. - Created by Kwon, Yeong Eon

/**
  * Represents a subscription
  *
  * @param filterPath  subscribed topic filter
  * @param qos         subscribed qos level [[QoS]]
  * @param actor       Actor that wraps actual subscriber (mqtt client, actor, callback function)
  * @param clientId    Holding remote client's [[ClientId]], only presets if the subscription is made by remote mqtt client.
  */
case class Registration(filterPath: FilterPath, qos: QoS, actor: ActorRef, clientId: Option[ClientId]) extends Ordered[Registration] {
  override def toString = s"${filterPath.toString} ($qos) => ${actor.path.toString}"

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case other: Registration =>
        actor == other.actor
      case _ => false
    }
  }

  override def compare(that: Registration): Int = {
    (this.clientId, that.clientId) match {
      case (Some(l), Some(r)) => l.id.compareToIgnoreCase(r.id)
      case (Some(_), None) => -1
      case (None, Some(_)) => 1
      case _ =>  this.actor.path.toString.compareToIgnoreCase(that.actor.path.toString)
    }
  }
}

object Registration extends DefaultJsonProtocol {
  implicit object RegistrationFormat extends RootJsonFormat[Registration] {
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

