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
