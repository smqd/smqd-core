package t2x.smqd.util

import java.io.IOException
import java.security.cert.{CertificateEncodingException, X509Certificate}

import org.bouncycastle.crypto.digests.{SHA1Digest, SHA3Digest}
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
import sun.misc.BASE64Encoder

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */
object SslUtil {
  def trimNewLines(input: String): String =
    input.replaceAll("-----BEGIN CERTIFICATE-----", "")
      .replaceAll("-----END CERTIFICATE-----", "")
      .replaceAll("\n", "")
      .replaceAll("\r", "")

  def getSha3Hash(data: String): String = {
    val trimmedData = trimNewLines(data)
    val dataBytes = trimmedData.getBytes
    val md = new SHA3Digest(256)
    md.reset()
    md.update(dataBytes, 0, dataBytes.length)
    val hashedBytes = new Array[Byte](256 / 8)
    md.doFinal(hashedBytes, 0)
    val sha3Hash = ByteUtils.toHexString(hashedBytes)
    sha3Hash
  }

  def getSha3HashFromHexCert(hexCert: String): String = {
    val hex2bytes = { hex : String =>
      hex.sliding(2,2).toArray.map(Integer.parseInt(_, 16).toByte)
    }

    val cert = new BASE64Encoder().encode(hex2bytes(hexCert))
    val sha3Hash = getSha3Hash(cert)
    sha3Hash
  }

  def getSha1Hash(data: String): String = {
    val trimmedData = trimNewLines(data)
    val dataBytes = trimmedData.getBytes
    val md = new SHA1Digest()
    md.reset()
    md.update(dataBytes, 0, dataBytes.length)
    val hashedBytes = new Array[Byte](256 / 8)
    md.doFinal(hashedBytes, 0)
    ByteUtils.toHexString(hashedBytes)
  }

  def getSha1HashFromHexCert(hexCert: String): String = {
    val hex2bytes = { hex : String =>
      hex.sliding(2,2).toArray.map(Integer.parseInt(_, 16).toByte)
    }

    val cert = new BASE64Encoder().encode(hex2bytes(hexCert))
    val digest = getSha1Hash(cert)
    digest
  }

  @throws[CertificateEncodingException]
  @throws[IOException]
  def getX509CertificateString(cert: X509Certificate): String = {
    trimNewLines( new BASE64Encoder().encode(cert.getEncoded))
  }

  @throws[javax.security.cert.CertificateEncodingException]
  @throws[IOException]
  def getX509CertificateString(cert: javax.security.cert.X509Certificate): String = {
    trimNewLines( new BASE64Encoder().encode(cert.getEncoded))
  }

}
