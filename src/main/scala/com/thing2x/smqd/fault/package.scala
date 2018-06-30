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

package com.thing2x.smqd

import spray.json._

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  */
package object fault {

  implicit object FaultFormat extends RootJsonFormat[Fault] {

    override def read(json: JsValue): Fault = {
      val jobj = json.asJsObject
      jobj.getFields("fault") match {
        case Seq(JsString(fault)) =>
          fault match {
            case "com.thing2x.smqd.fault.SessionFault" =>
              val f = jobj.getFields("sessionId", "message")
              SessionFault(f(0).toString, f(1).toString)
            case _ => GeneralFault(fault)
          }
      }
    }

    override def write(ft: Fault): JsValue = {
      ft match {
        case sf: SessionFault =>
          JsObject (
            "fault" -> JsString(sf.getClass.getName),
            "sessionId" -> JsString(sf.sessionId),
            "message" -> JsString(sf.message)
          )
        case gf: GeneralFault =>
          JsObject (
            "fault" -> JsString(gf.getClass.getName),
            "message" -> JsString(gf.message)
          )
        case _ =>
          JsObject (
            "fault" -> JsString(ft.getClass.getName)
          )
      }
    }
  }
}
