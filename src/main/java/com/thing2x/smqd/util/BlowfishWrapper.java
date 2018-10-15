package com.thing2x.smqd.util;
import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

// TODO: change Base64 codec
import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;


/**
 *
 */
public class BlowfishWrapper {
  private SecretKeySpec skeySpec;
  private Cipher cipher;

  public BlowfishWrapper(String key) throws GeneralSecurityException
  {
    cipher = Cipher.getInstance("Blowfish");
    skeySpec = new SecretKeySpec(key.getBytes(), "Blowfish");
  }

  public byte[] encode(byte[] plainText) throws GeneralSecurityException
  {
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
    return cipher.doFinal(plainText);
  }

  public byte[] decode(byte[] encryptedText) throws GeneralSecurityException
  {
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);
    return cipher.doFinal(encryptedText);
  }

  public String encodeWithBASE64(byte[] plainText) throws GeneralSecurityException
  {
    BASE64Encoder encoder = new BASE64Encoder();
    return encoder.encode(encode(plainText));
  }

  public byte[] decodeWithBASE64(String encryptedText) throws GeneralSecurityException, IOException
  {
    BASE64Decoder decoder = new BASE64Decoder();
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);
    return cipher.doFinal(decoder.decodeBuffer(encryptedText));
  }

}

