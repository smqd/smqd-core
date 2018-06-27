package t2x.smqd

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */

sealed trait SmqResult {
}

object SmqSuccess extends SmqResult

trait SmqFailure extends SmqResult
