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

import java.io.UnsupportedEncodingException;
import java.util.StringTokenizer;

// 2005. 4. 19 - Created by Kwon, Yeong Eon

/**
 *
 */
public class StringUtil
{
  public static int parseInt(String str, int def)
  {
    int rt = def;
    try
    {
      rt = (str == null || str.length() == 0 ? rt : Integer.parseInt(str));
    }
    catch (Throwable ignore)
    {
    }
    return rt;
  }

  /**
   * Method that splits a string with delimited fields into an array of field strings.
   *
   * @param str - the <code>String</code> that is splited by the delimeter.
   * @param delim - the delimeter string
   * @param trim - If it is true, trims the splited string.
   * @param fixedLen - fixed length of the array
   */
  public static String[] split(String str, String delim, boolean trim, int fixedLen)
  {
    if (str == null || str.length() == 0)
    {
      if (fixedLen <= 0)
        return null;
      else
        return new String[fixedLen];
    }

    StringTokenizer strtok = new StringTokenizer(str, delim);
    int count = strtok.countTokens();
    String[] result = new String[fixedLen > 0 ? fixedLen : count == 0 ? 1 : count];

    result[0] = str;

    for (int i = 0; i < count; i++)
    {
      if (fixedLen > 0 && i >= fixedLen)
        break;

      if (trim)
        result[i] = strtok.nextToken().trim();
      else
        result[i] = strtok.nextToken();
    }
    return result;
  }

  /**
   * Method that splits a string with delimited fields into an array of field strings.
   *
   * @param str - the <code>String</code> that is splited by the delimeter.
   * @param delim - the delimeter string
   * @param trim - If it is true, trims the splited string.
   */
  public static String[] split(String str, String delim, boolean trim)
  {
    return split(str, delim, trim, 0);
  }

  /**
   * Method that splits a string with delimited fields into an array of field strings.
   *
   * @param str - the <code>String</code> that is splited by the delimeter.
   * @param delimeter - the delimeter string
   */
  public static String[] split(String str, String delimeter)
  {
    return split(str, delimeter, false);
  }

  /**
   * Method that splits a string with delimited fields and return the count of items
   *
   * @param str - the <code>String</code> that is splited by the delimeter.
   * @param delimeter - the delimeter string
   */
  public static int splitCount(String str, String delimeter)
  {
    String[] result = split(str, delimeter, false);
    return result != null ? result.length : 0;
  }

  /**
   * Method that splits a string with delimited fields into an array of field strings.
   *
   * @param str - the <code>String</code> that is splited by the delimeter.
   * @param delimeter - the delimeter character
   */
  public static String[] split(String str, char delimeter)
  {
    return split(str, String.valueOf(delimeter));
  }

  public static String sprintf(String fmt, String arg1)
  {
    return vprintf(fmt, new String[]
            {
                    arg1
            });
  }

  public static String sprintf(String fmt, String arg1, String arg2)
  {
    return vprintf(fmt, new String[]
            {
                    arg1, arg2
            });
  }

  public static String sprintf(String fmt, String arg1, String arg2, String arg3)
  {
    return vprintf(fmt, new String[]
            {
                    arg1, arg2, arg3
            });
  }

  public static String sprintf(String fmt, String[] args)
  {
    return vprintf(fmt, args);
  }

  private static String vprintf(String fmt, String[] args)
  {
    StringBuffer sb = new StringBuffer();
    int argsIdx = 0;
    char c;
    int flags;
    int field_width;
    int precision;
    int base;
    long num;

    for (int i = 0; i < fmt.length(); i++)
    {
      c = fmt.charAt(i);

      if (c != '%')
      {
        sb.append(c);
        continue;
      }
      /* process flags */
      flags = 0;

      boolean repeat = true;
      while (repeat)
      {
        c = fmt.charAt(++i); /* this also skip first % */
        switch (c)
        {
          case '-':
            flags |= LEFT;
            break;
          case '+':
            flags |= PLUS;
            break;
          case ' ':
            flags |= SPACE;
            break;
          case '#':
            flags |= SPECIAL;
            break;
          case '0':
            flags |= ZEROPAD;
            break;
          default:
            repeat = false;
            break;
        }
      }

      /* get field width */
      field_width = -1;
      if (Character.isDigit(c))
      {
        field_width = 0;
        do
        {
          field_width = field_width * 10 + (c - '0');
          c = fmt.charAt(++i);
        } while (Character.isDigit(c));
      }
      else if (c == '*')
      {
        c = fmt.charAt(++i);
        /* it's the next argument */
        field_width = Integer.parseInt(args[argsIdx++]);
        if (field_width < 0)
        {
          field_width = -field_width;
          flags |= LEFT;
        }
      }

      /* get the precision */
      precision = -1;
      if (c == '.')
      {
        c = fmt.charAt(++i);
        if (Character.isDigit(c))
        {
          precision = 0;
          do
          {
            precision = precision * 10 + (c - '0');
            c = fmt.charAt(++i);
          } while (Character.isDigit(c));
        }
        else if (c == '*')
        {
          c = fmt.charAt(++i);
          /* it's the next argument */
          precision = Integer.parseInt(args[argsIdx++]);
        }
        if (precision < 0)
          precision = 0;
      }

      /* get the conversion qualifier */
      /*******************************************************************
       * NOT SUPPORT IN **JAVA qualifier = -1; if( c == 'h' || c == 'l' || c == 'L') { qualifier = c; c =
       * fmt.charAt(++i); }
       */

      /* default base */
      base = 10;
      switch (c)
      {
        case 'c':
          if ((flags & LEFT) == 0)
            while (--field_width > 0)
              sb.append(' ');

          sb.append(args[argsIdx++]);
          while (--field_width > 0)
            sb.append(' ');
          continue;

        case 's':
          String str = args[argsIdx++];
          if (str == null)
            str = "<null>";

          int len = str.length();

          if ((flags & LEFT) != 0)
            while (len < field_width--)
              sb.append(' ');
          sb.append(str);
          while (len < field_width--)
            sb.append(' ');
          continue;

          /*******************************************************************
           * NOT SUPPORT IN **JAVA case 'p': if( field_width == -1) { field_width = 2*sizeof(void *); flags |=
           * ZEROPAD; } print_number((unsigned long) va_arg(args, void *), 16, field_width, precision, flags);
           * continue;
           */
        case 'o':
          base = 8;
          break;

        /* integer number format */
        case 'X':
          flags |= LARGE;
        case 'x':
          base = 16;
          break;

        case 'd':
        case 'i':
          flags |= SIGN;
        case 'u':
          break;
        default:
          if (c != '%')
            sb.append('%').append(c);
          /*
           * XXX if (c != EOF) sb.append(c); else --i;
           */
          continue;
      }

      try
      {
        num = Long.parseLong(args[argsIdx++]);
        sb.append(vprintf_number(num, base, field_width, precision, flags));
      }
      catch (NumberFormatException nfe)
      {
        sb.append(args[argsIdx - 1]);
      }
    }

    return sb.toString();
  }

  private static String vprintf_number(long num, int base, int size, int precision, int type)
  {
    StringBuffer sb = new StringBuffer();

    char[] digits = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    char tmp[] = new char[66];
    char c;
    char sign;
    int i;

    if ((type & LARGE) != 0)
      digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    if ((type & LEFT) != 0)
      type &= ~ZEROPAD;

    if (base < 2 || base > 36)
      return "";

    c = ((type & ZEROPAD) != 0) ? '0' : ' ';
    sign = 0;

    if ((type & SIGN) != 0)
    {
      if (num < 0)
      {
        sign = '-';
        num = -num;
        size--;
      }
      else if ((type & PLUS) != 0)
      {
        sign = '+';
        size--;
      }
      else if ((type & SPACE) != 0)
      {
        sign = ' ';
        size--;
      }
    }

    if ((type & SPECIAL) != 0)
    {
      if (base == 16)
        size -= 2;
      else if (base == 8)
        size--;
    }

    i = 0;

    if (num == 0)
    {
      tmp[i++] = '0';
    }
    else
    {
      while (num != 0)
      {
        double d = num % base;
        tmp[i++] = digits[(int) d];
        num = num / base;
      }
    }

    if (i > precision)
      precision = i;

    size -= precision;

    if ((type & (ZEROPAD + LEFT)) == 0)
      while (size-- > 0)
        sb.append(' ');
    if (sign != 0)
      sb.append(sign);

    if ((type & SPECIAL) != 0)
    {
      if (base == 8)
      {
        sb.append('0');
      }
      else if (base == 16)
      {
        sb.append('0');
        sb.append(digits[33]);
      }
    }

    if ((type & LEFT) == 0)
      while (size-- > 0)
        sb.append(c);
    while (i < precision--)
      sb.append('0');
    while (i-- > 0)
      sb.append(tmp[i]);
    while (size-- > 0)
      sb.append(' ');

    return sb.toString();
  }

  private final static int ZEROPAD = 1; /* pad with zero */
  private final static int SIGN = 2; /* unsigned/signed long */
  private final static int PLUS = 4; /* show plus */
  private final static int SPACE = 8; /* space if plus */
  private final static int LEFT = 16; /* left justified */
  private final static int SPECIAL = 32; /* 0x */
  private final static int LARGE = 64; /* use 'ABCDEF' instead of 'abcdef' */

  /** str�� Wild Expression ('*'�� '?') ���ڸ� �����ϰ� �ִ��� Ȯ���Ѵ�. */
  public static boolean isWildExp(String str)
  {
    if (str == null)
      return false;
    if (str.indexOf('*') >= 0)
      return true;
    if (str.indexOf('?') >= 0)
      return true;
    return false;
  }

  /**
   * strstr�� strexp���� ������ wild express�� �մ����� Ȯ���Ѵ�. �� �� ��ҹ��ڸ� �������� �ʴ´�.
   *
   * @param strstr ���ϰ��� �ϴ� ���ڿ�
   * @param strexp '*', '?'�� �̿��� wild expression
   * @return �񱳰� �����ϸ� 0�� ��ȯ, �� ���� ��� �� Ȥ�� ���� ������ ��ȯ
   */
  public static int compareCaseWildExp(String strstr, String strexp)
  {
    int ret = 0;
    int x = 0, y = 0;
    if (strstr == null || strexp == null)
      return -1;
    char[] exp = strexp.toCharArray();
    char[] str = strstr.toCharArray();
    for (; y < exp.length; ++y, ++x)
    {
      if ((x >= str.length) && (exp[y] != '*'))
        return -1;
      if (exp[y] == '*')
      {
        while (++y < exp.length && exp[y] == '*')
          continue;
        // System.out.println("x = "+x+", y = "+y);
        if (y >= exp.length)
          return 0;
        while (x < str.length)
        {
          if ((ret = compareCaseWildExp(new String(str, x++, str.length - x + 1), new String(exp, y,
                  exp.length - y))) != 1)
            return ret;
        }
        return -1;
      }
      else if ((exp[y] != '?') && (Character.toLowerCase(str[x]) != Character.toLowerCase(exp[y])))
        return 1;
    }
    if (x < str.length)
      return 1;
    return 0;
  }

  /**
   * strstr�� strexp���� ������ wild express�� �մ����� Ȯ���Ѵ�. �� �� ��ҹ��ڸ� �����Ѵ�.
   *
   * @param strstr - the string that is compared.
   * @param strexp - '*', '?'�� �̿��� wild expression
   * @return �񱳰� �����ϸ� 0�� ��ȯ, �� ���� ��� �� Ȥ�� ���� ������ ��ȯ
   */
  public static int compareWildExp(String strstr, String strexp)
  {
    int ret = 0;
    int x = 0, y = 0;
    // System.out.println("strstr = "+strstr);
    // System.out.println("strexp = "+strexp);
    if (strstr == null || strexp == null)
      return -1;
    char[] exp = strexp.toCharArray();
    char[] str = strstr.toCharArray();
    for (; y < exp.length; ++y, ++x)
    {
      if ((x >= str.length) && (exp[y] != '*'))
        return -1;
      if (exp[y] == '*')
      {
        while (++y < exp.length && exp[y] == '*')
          continue;
        // System.out.println("x = "+x+", y = "+y);
        if (y >= exp.length)
          return 0;
        while (x < str.length)
        {
          if ((ret = compareWildExp(new String(str, x++, str.length - x + 1), new String(exp, y, exp.length
                  - y))) != 1)
            return ret;
        }
        return -1;
      }
      else if ((exp[y] != '?') && (str[x] != exp[y]))
        return 1;
    }
    if (x < str.length)
      return 1;
    return 0;
  }

  /**
   * str�ڿ� length���� ��ŭ space�� ä���.
   *
   * @param str - the string.
   * @param length - String total length.
   */
  public static String getANstring(String str, int length)
  {
    if (str == null)
    {
      str = "";
      for (int i = length; i > 0; --i)
      {
        str += " ";
      }
    }
    else
    {
      for (int i = length - str.getBytes().length; i > 0; --i)
      {
        str += " ";
      }
    }
    return str;
  }

  /**
   * str�տ� length���� ��ŭ '0'�� ä���.
   *
   * @param str - the string.
   * @param length - String total length.
   */
  public static String getNstring(String str, int length)
  {
    if (str == null)
    {
      str = "";
      for (int i = length; i > 0; --i)
      {
        str = "0" + str;
      }
    }
    else
    {
      for (int i = length - str.length(); i > 0; --i)
      {
        str = "0" + str;
      }
    }
    return str;
  }

  /**
   * intstr�տ� length���� ��ŭ '0'�� ä���.
   *
   * @param intstr - the integer.
   * @param length - String total length.
   */
  public static String getNstring(int intstr, int length)
  {
    String str = Integer.toString(intstr);
    for (int i = length - str.length(); i > 0; --i)
    {
      str = "0" + str;
    }
    return str;
  }

  /**
   * intstr�տ� length���� ��ŭ '0'�� ä���.
   *
   * @param longstr - the long.
   * @param length - String total length.
   */
  public static String getNstring(long longstr, int length)
  {
    String str = Long.toString(longstr);
    for (int i = length - str.length(); i > 0; --i)
    {
      str = "0" + str;
    }
    return str;
  }

  private final static String EMPTY_STRING = "";

  public static String getNNStr(String str)
  {
    return str != null ? str : EMPTY_STRING;
  }

  public static String getScriptNNStr(String str)
  {
    return str != null ? str.replaceAll("\\\\", "\\\\\\\\").replaceAll("\n", "\\\\n").replaceAll("\r\n", "\\\\n")
            .replaceAll("\"", "\\\\\"").replaceAll("\'", "\\\\\'") : EMPTY_STRING;
  }

  public static String dumpHex(String header, byte[] raw)
  {
    return dumpHex(header, raw, 0, raw.length);
  }

  private final static String table = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`~!@#$%^&*()-_=+[]{}\\|'\";:/?.,<>";

  public static boolean isPrintable(char c)
  {
    return table.indexOf(c) >= 0;
  }

  public static String dumpHex(String header, byte[] raw, int offset, int len)
  {
    int lastCount = 0;
    int count = 0;

    StringBuffer sb = new StringBuffer();
    sb.append(header).append("\n   ====================================================================");
    for (int idx = offset; count < len; idx++, count++)
    {
      if (count % 16 == 0)
      {
        lastCount = count;
        sb.append("\n      ");
      }

      if ((int) (raw[idx] & 0x000000FF) < 16)
        sb.append("0");
      sb.append(Integer.toString((int) (raw[idx] & 0x000000FF), 16).toUpperCase());

      if (count % 2 == 0)
        sb.append(" ");
      else
        sb.append("   ");

      if (count == len - 1)
      {
        for (int n = ((count + 1) % 16); n > 0 && n < 16; n++)
        {
          sb.append("   ");
          if (n % 2 == 1)
            sb.append("  ");
        }
      }

      if (count > 0 && (count + 1) % 16 == 0)
      {
        sb.append("        ");
        for (int n = lastCount; n <= count; n++)
        {
          if (isPrintable((char) raw[offset + n]))
            sb.append((char) raw[offset + n]).append(" ");
          else
            sb.append(". ");
        }
      }
      else if (count == len - 1)
      {
        sb.append("        ");
        for (int n = lastCount; n <= count; n++)
        {
          if (isPrintable((char) raw[offset + n]))
            sb.append((char) raw[offset + n]).append(" ");
          else
            sb.append(". ");
        }
      }
    }
    sb.append("\n   ====================================================================");

    return sb.toString();
  }

  public static String trimLeading(String s, char c)
  {
    if (s == null)
      return null;

    int i = 0;
    int len = s.length();

    for (; i < len; i++)
    {
      if (s.charAt(i) != c)
      {
        return s.substring(i, s.length());
      }
    }

    if (i == len)
      return EMPTY_STRING;

    return s;
  }

  public static String trimTrailing(String s, char c)
  {
    if (s == null)
      return null;

    int i = s.length() - 1;

    for (; i >= 0; i--)
    {
      if (s.charAt(i) != c)
      {
        return s.substring(0, i + 1);
      }
    }
    if (i == -1)
      return EMPTY_STRING;

    return s;
  }

  // ///////////////////////////////////////////////////
  // String utilities for ascii2native, native2ascii
  // ///////////////////////////////////////////////////
  static final String chars = "0123456789ABCDEFabcdef";

  public static String native2ascii(String s)
  {
    StringBuffer sb = new StringBuffer(s.length() + 80);
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (c <= 0xff)
      {
        sb.append(c);
      }
      else
      {
        sb.append("\\u" + Integer.toHexString((int) c).toUpperCase());
      }
    }
    return sb.toString();
  }

  public static String ascii2native(String s)
  {
    StringBuffer sb = new StringBuffer(s.length());
    int pos = 0;
    while (pos < s.length())
    {
      if (s.charAt(pos) == '\\')
      {
        if (pos + 5 < s.length() && s.charAt(pos + 1) == 'u' && isVoildHex(s, pos + 2))
        {
          sb.append(convert2native(s, pos + 2));
          pos += 6;
        }
        else
        {
          sb.append(s.charAt(pos++));
        }
      }
      else
      {
        sb.append(s.charAt(pos++));
      }
    }
    return sb.toString();
  }

  private static boolean isVoildHex(String s, int pos)
  {
    return chars.indexOf(s.charAt(pos)) != -1 && chars.indexOf(s.charAt(pos + 1)) != -1
            && chars.indexOf(s.charAt(pos + 2)) != -1 && chars.indexOf(s.charAt(pos + 3)) != -1;
  }

  private static String convert2native(String s, int start)
  {
    String tmp = s.substring(start, start + 4).toUpperCase();
    byte chs[] = new byte[2];
    int value = 16 * chars.indexOf(tmp.charAt(0)) + chars.indexOf(tmp.charAt(1));
    chs[0] = (byte) value;
    value = 16 * chars.indexOf(tmp.charAt(2)) + chars.indexOf(tmp.charAt(3));
    chs[1] = (byte) value;
    String ret;
    try
    {
      ret = new String(chs, "UTF-16");
    }
    catch (UnsupportedEncodingException ex)
    {
      ret = "\\u" + tmp;
    }
    return ret;
  }
}