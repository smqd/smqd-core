package com.uangel.smqd

import com.typesafe.config.ConfigFactory
import org.scalatest.FlatSpec
import t2x.smqd._

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  */
class SmqTlsProviderTest extends FlatSpec{

  "tsl provider" should "create from file" in {

    // 1. create a new keystore
    //    keytool -genkey -alias smqtest -keyalg RSA -keystore smqd-keystore.jks -keysize 2048
    //

    val conf = ConfigFactory.parseString(
      """
        | smqd {
        |   tls {
        |     storetype = jks
        |     keystore = ./src/main/resources/smqd-keystore.jks
        |     storepass = smqd-storepass
        |     keypass = smqd-keypass
        |   }
        | }
      """.stripMargin).resolve()

    val tlsConf = conf.getOptionConfig("smqd.tls")
    assert(tlsConf.isDefined)

    val tlsProvider = SmqTlsProvider(tlsConf.get)

    val engine = tlsProvider.sslEngine
    assert(engine.isDefined)
  }

  it should "create from resource" in {
    val conf = ConfigFactory.parseString(
      """
        | smqd {
        |   tls {
        |     storetype = jks
        |     keystore = smqd-keystore.jks
        |     storepass = smqd-storepass
        |     keypass = smqd-keypass
        |   }
        | }
      """.stripMargin).resolve()

    val tlsConf = conf.getOptionConfig("smqd.tls")
    assert(tlsConf.isDefined)

    val tlsProvider = SmqTlsProvider(tlsConf.get)

    val engine = tlsProvider.sslEngine
    assert(engine.isDefined)
  }
}
