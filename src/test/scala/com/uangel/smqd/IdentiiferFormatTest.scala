package t2x.smqd

import com.typesafe.config.ConfigFactory
import org.scalatest.FlatSpec

/**
  * 2018. 6. 1. - Created by Kwon, Yeong Eon
  */
class IdentiiferFormatTest extends FlatSpec {

  "Client Identifier" should "match" in {
    assert(valid("3"))
    assert(valid("n"))
    assert(valid("client_Identifier_123"))
    assert(valid("hong@t2x.com"))
    assert(valid("AE:09:UX"))
    assert(valid("192.168.1.1"))
    assert(valid("hong@127.0.0.1"))
    assert(valid("http://127.0.0.1:8000"))
    assert(valid("coap+tcp://127.0.0.1:123"))
    assert(valid("coap+tcp://127.0.0.1:123/~user"))
    assert(valid("coap+tcp://127.0.0.1:123/~user/some%20place"))
    assert(valid("coap+tcp://127.0.0.1:123/~user/some%20place?p=v"))
    assert(valid("user@coap+mqtt://127.0.0.1:8080/~user/some%20place?p=v&d=x"))

    assert(!valid(""))
    assert(!valid("$some"))
    assert(!valid("^done"))
    assert(!valid("한글"))
  }

  private val config = ConfigFactory.load("smqd-ref.conf")
  private val format = config.getString("smqd.core-mqtt.client.identifier.format").r

  private def valid(id: String): Boolean = {
    id match {
      case format(_*) => true
      case _ => false
    }
  }
}
