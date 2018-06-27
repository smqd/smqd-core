package t2x.smqd

import java.io.{File, FileInputStream, FileNotFoundException, IOException}
import java.security.KeyStore
import java.security.cert.{CertificateException, X509Certificate}

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl._
import t2x.smqd.net.mqtt.TLS
import t2x.smqd.util.SslUtil

import scala.collection.JavaConverters._

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  *
  * set following parameter to debug
  *
  *      -Djavax.net.debug=ssl
  */
object SmqTlsProvider {
  def apply(config: Config) = new SmqTlsProvider(config)
}

class SmqTlsProvider(config: Config) extends StrictLogging {

  private val keyStoreFile: String = config.getString("keystore")
  private val keyStoreType : String = config.getString("storetype")
  private val keyStorePassword : String = config.getString("storepass")
  private val keyPassword : String = config.getString("keypass")
  private val clientCredentialsService: Any = null

  def sslEngine: Option[SSLEngine] = {
    if (keyStoreFile == ""){
      logger.warn("SSL keyStore NOT defined")
      return None
    }

    try {
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
      trustStore.load( tsStream, keyStorePassword.toCharArray)
      tmFactory.init(trustStore)
      tsStream.close()

      val ksStream = if (file.exists)
        new FileInputStream(file)
      else
        getClass.getClassLoader.getResourceAsStream(keyStoreFile)

      val ks = KeyStore.getInstance(keyStoreType)
      ks.load( ksStream, keyStorePassword.toCharArray)
      ksStream.close()

      logger.info("SSL keystore contains: {}", ks.aliases().asScala.mkString(", "))
      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      kmf.init(ks, keyPassword.toCharArray)
      val km = kmf.getKeyManagers

      tmFactory.getTrustManagers.find(tm => tm.isInstanceOf[X509TrustManager]) match {
        case Some(x509tm: X509TrustManager) =>
          val x509wrapped = new X509TrustManagerWrapper(x509tm, clientCredentialsService )

          val tm: Array[TrustManager] = Array(x509wrapped)
          val sslContext = SSLContext.getInstance(TLS)
          sslContext.init(km, tm, null)

          val sslEngine = sslContext.createSSLEngine
          sslEngine.setUseClientMode(false)
          sslEngine.setNeedClientAuth(false)
          sslEngine.setWantClientAuth(true)
          sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols)
          sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites)
          sslEngine.setEnableSessionCreation(true)

          logger.trace("SSL supporting protocols: {}", sslEngine.getSupportedProtocols.mkString(", "))
          logger.trace("SSL supporting cipher suites: {}", sslEngine.getSupportedCipherSuites.mkString(", "))
          Some(sslEngine)
        case _ =>
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
      logger.trace("=======>issuer")

      trustManager.getAcceptedIssuers
    }

    @throws[CertificateException]
    override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {
      logger.trace("=======>server trust")
      trustManager.checkServerTrusted(chain, authType)
    }

    @throws[CertificateException]
    override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {

      logger.trace(s"=======>$authType\n{}", chain)
      if (chain.isEmpty){
        new CertificateException("Empty Client Certificate")
      }
      else {
        try {
          chain.find {  cert =>
            val strCert = SslUtil.getX509CertificateString(cert)
            val hashCert = SslUtil.getSha3Hash(strCert)
            // TODO: get device certificates from DB
            //            val deviceCredentials = deviceCredentialsService.findDeviceCredentialsByCredentialsId(sha3Hash)
            //            if (deviceCredentials != null && strCert == deviceCredentials.getCredentialsValue) true
            true
          } match {
            case Some(x509) =>
              // cert check ok
            case _ =>
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
}
