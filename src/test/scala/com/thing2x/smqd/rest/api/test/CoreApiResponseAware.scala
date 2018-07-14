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

package com.thing2x.smqd.rest.api.test

import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.typesafe.scalalogging.StrictLogging
import spray.json._

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */

trait CoreApiResponseAware extends DefaultJsonProtocol with StrictLogging {

  case class CoreApiResponse(code: Int, result: Map[String, JsValue])

  implicit val CoreApiResultFormat: RootJsonFormat[CoreApiResponse] = jsonFormat2(CoreApiResponse)

  def asCoreApiResponse(jsonString: String): CoreApiResponse = {
    logger.debug(jsonString)
    val json = jsonString.parseJson
    json.convertTo[CoreApiResponse]
  }
}
