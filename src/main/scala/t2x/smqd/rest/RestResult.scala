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

