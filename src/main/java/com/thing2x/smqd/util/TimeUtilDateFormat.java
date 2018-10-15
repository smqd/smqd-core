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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

// 10/15/18 - Created by Kwon, Yeong Eon

/**
 *
 */
public class TimeUtilDateFormat
{

  private SimpleDateFormat sdf;

  public TimeUtilDateFormat(String format)
  {
    sdf = new SimpleDateFormat(format);
  }

  public synchronized String format(long tick)
  {
    return sdf.format(tick);
  }

  public synchronized String format(Date date)
  {
    return sdf.format(date);
  }

  public synchronized Date parse(String string) throws ParseException
  {
    return sdf.parse(string);
  }

  public synchronized Object parseObject(String string) throws ParseException
  {
    return sdf.parse(string);
  }

}
