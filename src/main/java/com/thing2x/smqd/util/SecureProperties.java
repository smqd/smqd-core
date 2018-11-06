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
import java.util.Properties;

// 10/15/18 - Created by Kwon, Yeong Eon

public class SecureProperties extends Properties
{
  private static final long serialVersionUID = -8003069113149770808L;

  private BlowfishWrapper bw;

  public SecureProperties()
          throws Exception
  {
    bw = new BlowfishWrapper("uangel.umsp");
  }

  public Object setProperty(String key, String value)
  {
    return setProperty(key, value, false);
  }

  public Object setProperty(String key, String value, boolean secure)
  {
    try
    {
      if (secure)
      {
        String enc = bw.encodeWithBASE64(value.getBytes("utf-8"));
        return super.setProperty(key, "@SEC" + enc);
      }
      else
      {
        return super.setProperty(key, value);
      }
    }
    catch (Exception e)
    {
      return null;
    }
  }

  public String getProperty(String key)
  {
    String val = super.getProperty(key);
    if (val == null)
      return val;

    try
    {
      if (val.startsWith("@SEC"))
      {
        return new String(bw.decodeWithBASE64(val.substring(4)));
      }
      else
      {
        return val;
      }
    }
    catch (Exception e)
    {
      return null;
    }
  }

  public String encodeString(String value) throws Exception
  {
    if (value == null)
      return null;

    return "@SEC" + bw.encodeWithBASE64(value.getBytes("utf-8"));
  }

  public String decodeString(String str) throws Exception
  {
    if (str.startsWith("@SEC"))
      return new String(bw.decodeWithBASE64(str.substring(4)), "utf-8");
    else
      return new String(bw.decodeWithBASE64(str), "utf-8");
  }

  public static void main(String[] args) throws Exception
  {
    if (args.length != 2)
    {
      System.out.println("Usage: java -classpath <path> x3.util.vault.SecureProperties <cmd> args");
      System.out.println("     <cmd> :  enc | dec ");
      return;
    }

    SecureProperties prop = new SecureProperties();

    if ("enc".equalsIgnoreCase(args[0]))
    {
      System.out.println(prop.encodeString(args[1]));
    }
    else if ("dec".equalsIgnoreCase(args[0]))
    {
      System.out.println(prop.decodeString(args[1]));
    }
  }
}
