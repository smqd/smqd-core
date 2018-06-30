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

import java.io.{File, FileInputStream, FileNotFoundException, IOException}
import java.security.{KeyStore, SecureRandom}
import java.security.cert.{CertificateException, X509Certificate}

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl._
import com.thing2x.smqd.net.mqtt.TLS
import com.thing2x.smqd.util.SslUtil

import scala.collection.JavaConverters._

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  *
  * set following parameter to debug
  *
  *      -Djavax.net.debug=ssl
  */
object TlsProvider {
  def apply(config: Config) = new TlsProvider(config)
}

class TlsProvider(config: Config) extends StrictLogging {

  private val keyStoreFile: String = config.getString("keystore")
  private val keyStoreType : String = config.getString("storetype")
  private val keyStorePassword : String = config.getString("storepass")
  private val keyPassword : String = config.getString("keypass")
  private val clientCredentialsService: Any = null

  private def trustManagers: Array[TrustManager] = {
    val file = new File(keyStoreFile)
    val tsStream = if (file.exists) {
      logger.info(s"SSL keystore file: $keyStoreFile")
      new FileInputStream(file)
    }
    else {
      val loader = Thread.currentThread().getContextClassLoader
      val in = loader.getResourceAsStream(keyStoreFile)
      if (in == null)
        throw new FileNotFoundException(s"SSL keystore not found: $keyStoreFile")
      else
        logger.info(s"SSL keystore resource: $keyStoreFile")
      in
    }

    val tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    val trustStore = KeyStore.getInstance(keyStoreType)
    trustStore.load(tsStream, keyStorePassword.toCharArray)
    tsStream.close()

    tmFactory.init(trustStore)

    tmFactory.getTrustManagers.filter(tm => tm.isInstanceOf[X509TrustManager])
  }

  private def keyManagers: Array[KeyManager] = {
    val file = new File(keyStoreFile)
    val ksStream = if (file.exists) {
      new FileInputStream(file)
    }
    else {
      val loader = Thread.currentThread().getContextClassLoader
      val in = loader.getResourceAsStream(keyStoreFile)
      if (in == null)
        throw new FileNotFoundException(s"SSL keystore not found: $keyStoreFile")
      in
    }

    val ks = KeyStore.getInstance(keyStoreType)
    ks.load( ksStream, keyStorePassword.toCharArray)
    ksStream.close()

    logger.info("SSL keystore contains: {}", ks.aliases().asScala.mkString(", "))
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, keyPassword.toCharArray)
    val km = kmf.getKeyManagers
    km
  }

  def sslContext: Option[SSLContext] = {
    val tms = trustManagers
    val kms = keyManagers
    val sslContext = SSLContext.getInstance(TLS)
    sslContext.init(kms, tms, new SecureRandom())
    Some(sslContext)
  }

  def sslEngine: Option[SSLEngine] = {
    if (keyStoreFile == ""){
      logger.warn("SSL keyStore NOT defined")
      return None
    }

    try {
      val tms = trustManagers
      val kms = keyManagers

      if (tms.nonEmpty && tms.head.isInstanceOf[X509TrustManager]) {
        val x509tm = tms.head.asInstanceOf[X509TrustManager]
        val x509wrapped = new X509TrustManagerWrapper(x509tm, clientCredentialsService )

        val tmsWrapped: Array[TrustManager] = Array(x509wrapped)
        val sslContext = SSLContext.getInstance(TLS)
        sslContext.init(kms, tmsWrapped, new SecureRandom())

        val sslEngine = sslContext.createSSLEngine
        sslEngine.setUseClientMode(false)
        sslEngine.setNeedClientAuth(false)
        sslEngine.setWantClientAuth(true)

        // sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols)
        // we may deny old version TLS
        val supportingProtocols = Array("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2")
        sslEngine.setEnabledProtocols(supportingProtocols)
        logger.trace("SSL supporting protocols: {}", sslEngine.getSupportedProtocols.mkString(", "))

        sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites)
        sslEngine.setEnableSessionCreation(true)
        //logger.trace("SSL supporting cipher suites: {}", sslEngine.getSupportedCipherSuites.mkString(", "))

        Some(sslEngine)
      }
      else {
        None
      }
    }
    catch {
      case e: Throwable =>
        logger.error("Unable to set up SSL context. Reason: " + e.getMessage, e)
        scala.sys.exit(3)
        None
    }
  }

  private class X509TrustManagerWrapper(val trustManager: X509TrustManager, val clientCredentialsService: Any)
    extends X509TrustManager
      with StrictLogging {

    override def getAcceptedIssuers: Array[X509Certificate] = {
      trustManager.getAcceptedIssuers
    }

    @throws[CertificateException]
    override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {
      trustManager.checkServerTrusted(chain, authType)
    }

    @throws[CertificateException]
    override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {
      logger.trace("SSL certificate ... chain")
      if (chain.isEmpty){
        new CertificateException("Empty Client Certificate")
      }
      else {
        try {
          chain.find {  cert =>
            val strCert = SslUtil.getX509CertificateString(cert)
            val hashCert = SslUtil.getSha1Hash(strCert)
            verifyX509HashCert(hashCert, authType)
          } match {
            case Some(x509) =>
              logger.trace("SSL certificate ... ok")
              // cert check ok
            case _ =>
              logger.trace("SSL certificate ... no cert -1")
              new CertificateException("Invalid Client Certificate")
          }

        } catch {
          case e: IOException =>
            logger.error(e.getMessage, e)
            new CertificateException("Invalid Client Certificate")
        }
      }
    }
  }

  private def verifyX509HashCert(hashCert: String, authType: String): Boolean = {
    logger.trace(s"SSL X.509 Certificate: $authType, #: $hashCert")
    // TODO: get device certificates from DB
    //            val deviceCredentials = deviceCredentialsService.findDeviceCredentialsByCredentialsId(sha3Hash)
    //            if (deviceCredentials != null && strCert == deviceCredentials.getCredentialsValue) true
    true
  }
}
