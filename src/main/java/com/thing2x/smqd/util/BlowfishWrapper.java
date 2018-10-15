/*
 * Copyright 2018 UANGEL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thing2x.smqd.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

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

  public String encodeWithBASE64(byte[] plainText)
  {
    return new String(Base64.getEncoder().encode(plainText));
  }

  public byte[] decodeWithBASE64(String encryptedText) throws GeneralSecurityException
  {
    Base64.Decoder decoder = Base64.getDecoder();
    cipher.init(Cipher.DECRYPT_MODE, skeySpec);
    return cipher.doFinal(decoder.decode(encryptedText));
  }

}

