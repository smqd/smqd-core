package com.uangel.smqd

import com.typesafe.config.ConfigFactory
import org.scalatest.FlatSpec
import t2x.smqd._

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  */
class TlsProviderTest extends FlatSpec{

  "tsl provider" should "create from file" in {

    val conf = ConfigFactory.parseString(
      """
        | smqd {
        |   tls {
        |     storetype = jks
        |     keystore = ./src/test/tls/keygen/smqd-server.jks
        |     storepass = smqd.demo.key
        |     keypass = smqd.demo.key
        |   }
        | }
      """.stripMargin).resolve()

    val tlsConf = conf.getOptionConfig("smqd.tls")
    assert(tlsConf.isDefined)

    val tlsProvider = TlsProvider(tlsConf.get)

    val engine = tlsProvider.sslEngine
    assert(engine.isDefined)
  }
}
