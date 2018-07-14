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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.http.HttpServiceContext
import com.typesafe.config.Config
import spray.json._

// 2018. 6. 20. - Created by Kwon, Yeong Eon

abstract class RestController(name: String, context: HttpServiceContext) extends RestResult with DefaultJsonProtocol {

  /**
    * Deprecated, use [[RestController]]'s new Constructor
    */
  @deprecated("use new constructor (name: String, context: HttpServiceContext)", since="0.4.0")
  def this(name: String, smqd: Smqd, config: Config) = this(name, new HttpServiceContext(null, null, smqd, config))

  def routes: Route

  def pagenate[T](objects: Iterable[T], currPageOpt: Option[Int], pageSizeOpt: Option[Int])(implicit jsonWriter: JsonWriter[Iterable[T]]): JsValue = {
    var currPage = currPageOpt.getOrElse(1)
    var pageSize = pageSizeOpt.getOrElse(20)

    val totalNum = objects.size
    val totalPage = (totalNum + pageSize - 1)/pageSize

    currPage = math.max(math.min(currPage, totalPage), 1)
    pageSize = math.max(math.min(pageSize, 100), 1)

    val from = (currPage - 1) * pageSize
    val until = from + pageSize

    val sliced = objects match {
      case _ => objects.slice(from, until)
    }

    JsObject(
      "current_page" -> JsNumber(currPage),
      "page_size" -> JsNumber(pageSize),
      "total_num" -> JsNumber(totalNum),
      "total_page" -> JsNumber(totalPage),
      "objects" -> jsonWriter.write(sliced)
    )
  }
}
