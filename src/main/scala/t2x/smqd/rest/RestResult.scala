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

package t2x.smqd.rest

import spray.json.{JsNumber, JsObject, JsString, JsValue}

/**
  * 2018. 6. 20. - Created by Kwon, Yeong Eon
  */
trait RestResult {
  def restSuccess(code: Int, result: JsValue): JsValue = JsObject(
    "code" -> JsNumber(code),
    "result" -> result
  )
  def restError(code: Int, message: String): JsValue = JsObject(
    "code" -> JsNumber(code),
    "error" -> JsString(message)
  )
}

