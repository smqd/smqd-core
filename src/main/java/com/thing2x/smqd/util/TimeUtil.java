package com.thing2x.smqd.util;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

//  2005. 3. 11- Created by Kwon, Yeong Eon

/**
 *
 */
public class TimeUtil
{
  /** Format pattern HTTP = "EEE, dd MMM yyyy HH:mm:ss 'GMT'" */
  public static final int HTTP = 1;

  /** Format pattern XLF = "yyyy/MMM/dd HH:mm:ss" */
  public static final int XLF = 2;

  /** Format pattern CLF = "dd/MMM/yyyy:HH:mm:ss" */
  public static final int CLF = 3;

  /** Format pattern ROLLOVER = "yyyyMMdd_HHmmss" */
  public static final int ROLLOVER = 4;

  /** Format pattern STANDARD = "yyyy/MM/dd HH:mm:ss z" */
  public static final int STANDARD = 5;

  /** Format pattern SIMPLE = "yyyy/MM/dd HH:mm:ss" */
  public static final int SIMPLE = 6;

  /** Format pattern FMT8 = "yyyyMMdd" */
  public static final int FMT8 = 10;

  /** Format pattern FMT8EN = "dd/MM/yy" */
  public static final int FMT8EN = 11;

  /** Format pattern FMT8AM = "MM/dd/yy" */
  public static final int FMT8AM = 12;

  private static TimeUtilDateFormat formatter_rollover = new TimeUtilDateFormat("yyyyMMdd_HHmmss");
  private static TimeUtilDateFormat formatter_standard = new TimeUtilDateFormat("yyyy/MM/dd HH:mm:ss z");
  private static TimeUtilDateFormat formatter_simple = new TimeUtilDateFormat("yyyy/MM/dd HH:mm:ss");

  public final static TimeUtilDateFormat fmt14 = new TimeUtilDateFormat("yyyyMMddHHmmss");
  public final static TimeUtilDateFormat fmt12 = new TimeUtilDateFormat("yyyyMMddHHmm");
  public final static TimeUtilDateFormat fmt10 = new TimeUtilDateFormat("yyyyMMddHH");
  public final static TimeUtilDateFormat fmt8 = new TimeUtilDateFormat("yyyyMMdd");
  public final static TimeUtilDateFormat fmt8EN = new TimeUtilDateFormat("dd/MM/yyyy");
  public final static TimeUtilDateFormat fmt8AM = new TimeUtilDateFormat("MM/dd/yyyy");
  public final static TimeUtilDateFormat fmtlong = new TimeUtilDateFormat("yyyy/MM/dd HH:mm:ss");
  public final static TimeUtilDateFormat fmt17 = new TimeUtilDateFormat("yyyyMMddHHmmssSSS");

  private Calendar calendar;
  private NumberFormat numberFormat;
  private Date m_date;
  private int m_format; // format style

  private static long millisPerHour = 0x36ee80L;
  private static long millisPerMinute = 60000L;

  /**
   * Constructs a DataUtil with the specified number of milliseconds.
   *
   * @param date - the milliseconds since January 1, 1970, 00:00:00 GMT.
   */
  public TimeUtil(long date)
  {
    calendar = Calendar.getInstance();
    numberFormat = NumberFormat.getInstance();
    m_date = new Date(date);
    m_format = STANDARD;
  }

  /**
   * Constructs a DataUtil with the specified number of milliseconds and format pattern.
   *
   * @param date - the milliseconds since January 1, 1970, 00:00:00 GMT.
   * @param format - A format pattern code defined by <code>enet.framework.util.DateUtil<code>
   */
  public TimeUtil(long date, int format)
  {
    this(date);
    setDefaultFormat(format);
  }

  /**
   * Sets default format style. The format style is defined in this class
   */
  public void setDefaultFormat(int format)
  {
    m_format = format;
  }

  @SuppressWarnings("unused")
  private String getZoneOffset()
  {
    StringBuffer sb = new StringBuffer();

    int i = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET);

    if (i < 0)
    {
      sb.append("-");
      i = -i;
    }
    else
    {
      sb.append("+");
    }

    sb.append(zeroPad((int) ((long) i / millisPerHour), 2, 2));
    sb.append(zeroPad((int) (((long) i % millisPerHour) / millisPerMinute), 2, 2));
    return sb.toString();
  }

  private String zeroPad(long l, int minint, int maxint)
  {
    numberFormat.setMinimumIntegerDigits(minint);
    numberFormat.setMaximumIntegerDigits(maxint);
    return numberFormat.format(l);
  }

  protected long parse(String str) throws ParseException
  {
    SimpleDateFormat sdf = null;
    Date date = null;

    try
    {
      sdf = new SimpleDateFormat("E, dd MMM yyy HH:mm:ss 'GMT'", Locale.US);
      sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
      date = sdf.parse(str);

      return date.getTime();
    }
    catch (ParseException ex)
    {
    }

    try
    {
      sdf = new SimpleDateFormat("E, dd-MMM-yy HH:mm:ss 'GMT'", Locale.US);
      sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
      date = sdf.parse(str);

      return date.getTime();
    }
    catch (ParseException ex)
    {
      sdf = new SimpleDateFormat("E MMM dd HH:mm:ss yyyy", Locale.US);
    }

    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    date = sdf.parse(str);

    return date.getTime();
  }

  /**
   * <PRE>
   *
   * Parse the date string with the given JDK format pattern. The pattern
   * symbols goes with those of JDK.
   *
   * </PRE>
   *
   * @param dateString
   * @param pattern JDK's formatting pattern
   */
  public static Date parse(String dateString, String pattern) throws ParseException
  {
    SimpleDateFormat sdf = new SimpleDateFormat(pattern);
    return sdf.parse(dateString);
  }

  public String format()
  {
    return format(m_format);
  }

  public String format(int format)
  {
    switch (format)
    {
      case ROLLOVER:
        return formatForRollover();
      case STANDARD:
        return formatForStandard();
      case SIMPLE:
        return formatForSimple();
    }
    return m_date.toString();
  }

  private String formatForRollover()
  {
    synchronized (formatter_rollover)
    {
      return formatter_rollover.format(m_date);
    }
  }

  private String formatForStandard()
  {
    synchronized (formatter_standard)
    {
      return formatter_standard.format(m_date);
    }
  }

  private String formatForSimple()
  {
    synchronized (formatter_simple)
    {
      return formatter_simple.format(m_date);
    }
  }

  public static String getCurrentTimeAs14Format()
  {
    return getCurrentTimeAs14Format(0);
  }

  public static String getCurrentTimeAs14Format(int dayDuration)
  {
    long tick = System.currentTimeMillis();
    return getCurrentTimeAs14Format(dayDuration, tick);
  }

  public static String getCurrentTimeAs14Format(int dayDuration, long tick)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� tick +=
     * (long)dayDuration * 24 * 60 * 60 * 1000; return fmt14.format(new java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(tick);

    c.add(Calendar.DATE, dayDuration);

    return fmt14.format(c.getTime());
  }

  public static String getCurrentTimeAs12Format()
  {
    return getCurrentTimeAs12Format(0);
  }

  public static String getCurrentTimeAs12Format(int dayDuration)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� long tick =
     * System.currentTimeMillis(); tick += (long)dayDuration * 24 * 60 * 60 * 1000; return fmt12.format(new
     * java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.setTime(new Date());

    c.add(Calendar.DATE, dayDuration);

    return fmt12.format(c.getTime());
  }

  public static String getCurrentTimeAs12FormatByMin(int minDuration)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� long tick =
     * System.currentTimeMillis(); tick += (long)minDuration * 60 * 1000; return fmt12.format(new
     * java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.setTime(new Date());

    c.add(Calendar.MINUTE, minDuration);

    return fmt12.format(c.getTime());
  }

  public static String get14StrFormatFrom14FormatByMin(String time, int minDuration)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� long tick =
     * getTickFrom14StrFormat(time); tick += (long)minDuration * 60 * 1000; return fmt14.format(new
     * java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.set(Calendar.YEAR, Integer.parseInt(time.substring(0, 4)));
    c.set(Calendar.MONTH, Integer.parseInt(time.substring(4, 6)) - 1);
    c.set(Calendar.DATE, Integer.parseInt(time.substring(6, 8)));
    c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(8, 10)));
    c.set(Calendar.MINUTE, Integer.parseInt(time.substring(10, 12)));
    c.set(Calendar.SECOND, Integer.parseInt(time.substring(12, 14)));

    c.add(Calendar.MINUTE, minDuration);

    return fmt14.format(c.getTime());
  }

  public static String getTimeAs14FormatByHour(java.util.Date date, int hourDuration)
  {
    if (date == null)
      return null;
    String strDate = fmt14.format(date);
    return get14StrFormatFrom14FormatByHour(strDate, hourDuration);
  }

  public static String get14StrFormatFrom14FormatByHour(String time, int hourDuration)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� long tick =
     * getTickFrom14StrFormat(time); tick += (long)hourDuration * 60 * 60 * 1000; return fmt14.format(new
     * java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.set(Calendar.YEAR, Integer.parseInt(time.substring(0, 4)));
    c.set(Calendar.MONTH, Integer.parseInt(time.substring(4, 6)) - 1);
    c.set(Calendar.DATE, Integer.parseInt(time.substring(6, 8)));
    c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(8, 10)));
    c.set(Calendar.MINUTE, Integer.parseInt(time.substring(10, 12)));
    c.set(Calendar.SECOND, Integer.parseInt(time.substring(12, 14)));

    c.add(Calendar.HOUR_OF_DAY, hourDuration);

    return fmt14.format(c.getTime());
  }

  public static String get14StrFormatFrom14FormatByDay(String time, int dayDuration)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� long tick =
     * getTickFrom14StrFormat(time); tick += (long)dayDuration * 24 * 60 * 60 * 1000; return fmt14.format(new
     * java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.set(Calendar.YEAR, Integer.parseInt(time.substring(0, 4)));
    c.set(Calendar.MONTH, Integer.parseInt(time.substring(4, 6)) - 1);
    c.set(Calendar.DATE, Integer.parseInt(time.substring(6, 8)));
    c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(8, 10)));
    c.set(Calendar.MINUTE, Integer.parseInt(time.substring(10, 12)));
    c.set(Calendar.SECOND, Integer.parseInt(time.substring(12, 14)));

    c.add(Calendar.DATE, dayDuration);

    return fmt14.format(c.getTime());
  }

  public static String getCurrentTimeAs10Format()
  {
    return getCurrentTimeAs10Format(0);
  }

  public static String getCurrentTimeAs10Format(int dayDuration)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� long tick =
     * System.currentTimeMillis(); tick += (long)dayDuration * 24 *60 * 60 * 1000; return fmt10.format(new
     * java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.setTime(new Date());

    c.add(Calendar.DATE, dayDuration);

    return fmt10.format(c.getTime());
  }

  public static String getCurrentTimeAs10FormatByHour(int hourDuration)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� long tick =
     * System.currentTimeMillis(); tick += (long)hourDuration * 60 * 60 * 1000; return fmt10.format(new
     * java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.setTime(new Date());

    c.add(Calendar.HOUR_OF_DAY, hourDuration);

    return fmt10.format(c.getTime());
  }

  public static String getCurrentTimeAs17Format()
  {
    long tick = System.currentTimeMillis();
    return fmt17.format(new java.util.Date(tick));
  }

  public static String getCurrentTimeAsLongFormat()
  {
    long tick = System.currentTimeMillis();
    return fmtlong.format(new java.util.Date(tick));
  }

  public static String getCurrentTimeAs8Format()
  {
    return getCurrentTimeAs8Format(0);
  }

  public static String getCurrentTimeAs8Format(int dayDuration)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� long tick =
     * System.currentTimeMillis(); tick += (long)dayDuration * 24 * 60 * 60 * 1000; return fmt8.format(new
     * java.util.Date(tick));
     */

    Calendar c = Calendar.getInstance();
    c.setTime(new Date());

    c.add(Calendar.DATE, dayDuration);

    return fmt8.format(c.getTime());
  }

  public static String getCurrentTimeAs8Format(int dayDuration, long tick)
  {
    return getCurrentTimeAs8Format(dayDuration, tick, FMT8);
  }

  public static String getCurrentTimeAs8Format(int dayDuration, long tick, int fmt)
  {
    /*
     * daylight saving time ���� ���� �߻��� ��� �ð��� ����� ���� -> ���������� tick ��ȯ ���� tick +=
     * (long)dayDuration * 24 * 60 * 60 * 1000;
     */

    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(tick);

    c.add(Calendar.DATE, dayDuration);

    if (fmt == FMT8)
      return fmt8.format(c.getTime());
    else if (fmt == FMT8EN)
      return fmt8EN.format(c.getTime());
    else if (fmt == FMT8AM)
      return fmt8AM.format(c.getTime());
    else
      return null;
  }

  public static Date addDay(java.util.Date date, int dayDuration)
  {
    if (date == null)
      return null;

    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    cal.add(Calendar.DATE, dayDuration);
    return cal.getTime();
  }

  public static String getTimeAs14Format(java.util.Date date)
  {
    if (date == null)
      return null;

    return fmt14.format(date);
  }

  public static String getTimeAs8Format(java.util.Date date)
  {
    if (date == null)
      return null;

    return getTimeAs8Format(date, FMT8);
  }

  public static String getTimeAs8Format(int fmt)
  {
    long tick = System.currentTimeMillis();
    return getTimeAs8Format(new java.util.Date(tick), fmt);
  }

  public static String getTimeAs8Format(java.util.Date date, int fmt)
  {
    if (date == null)
      return null;

    if (fmt == FMT8)
      return fmt8.format(date);
    else if (fmt == FMT8EN)
      return fmt8EN.format(date);
    else if (fmt == FMT8AM)
      return fmt8AM.format(date);
    else
      return null;
  }

  public static java.sql.Date toDateAs14Format(String str)
  {
    if (str == null || str.length() != 14)
      return null;

    /*
     * java.sql.Date rt = null;
     *
     * int year = Integer.parseInt(str.substring(0, 4)); int month = Integer.parseInt(str.substring(4, 6)); int day
     * = Integer.parseInt(str.substring(6, 8)); int hh = Integer.parseInt(str.substring(8, 10)); int mm =
     * Integer.parseInt(str.substring(10, 12)); int ss = Integer.parseInt(str.substring(12, 14));
     *
     * java.util.Date dd = new java.util.Date(year, month, day, hh, mm, ss); rt = new java.sql.Date(dd.getTime());
     * return rt;
     */
    try
    {
      return new java.sql.Date(fmt14.parse(str).getTime());
    }
    catch (ParseException e)
    {
      e.printStackTrace();
    }
    return null;
  }

  public static long getTickFrom14StrFormat(String time)
  {
    Calendar c = Calendar.getInstance();
    try
    {
      c.setTime(new java.text.SimpleDateFormat("yyyyMMddHHmmss").parse(time));
    }
    catch (ParseException e)
    {
      e.printStackTrace();
    }
    return c.getTimeInMillis();
  }

  public static String get14StrFormatFromTick(long tick)
  {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(tick);

    return new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(c.getTime());
  }

  public static int getDayOfWeek()
  {
    long tick = System.currentTimeMillis();
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(tick);

    return c.get(Calendar.DAY_OF_WEEK);
  }

  public static int getDayOfMonth()
  {
    long tick = System.currentTimeMillis();
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(tick);

    return c.get(Calendar.DAY_OF_MONTH);
  }

  public static int getDayOfYear()
  {
    long tick = System.currentTimeMillis();
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(tick);

    return c.get(Calendar.DAY_OF_YEAR);
  }

  public static boolean isAvailablePeriod(Date beginTime, Date endTime, Date specificTime)
  {
    boolean isValid = false;

    if (specificTime == null)
      specificTime = new Date();

    if (beginTime == null || endTime == null)
    {
      isValid = true;
    }
    else
    {
      if (specificTime.after(beginTime) && specificTime.before(endTime))
        isValid = true;
    }

    return isValid;
  }
}