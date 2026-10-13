package com.ruoyi.common.redis;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateUtil {

    private final static SimpleDateFormat sdfYear = new SimpleDateFormat("yyyy");

    private final static SimpleDateFormat sdfDay = new SimpleDateFormat("yyyy-MM-dd");

    private final static SimpleDateFormat sdfHour = new SimpleDateFormat("yyyy-MM-dd HH");

    private final static SimpleDateFormat sdfDays = new SimpleDateFormat("yyyyMMdd");

    private final static SimpleDateFormat sdfYearMonth = new SimpleDateFormat("YYMM");

    private final static SimpleDateFormat sdfLongYearMonth = new SimpleDateFormat("yyyy-MM");

    private final static SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取YYYY格式
     *
     * @return
     */
    public static String getYear() {
        return sdfYear.format(new Date());
    }

    /**
     * 获取YYYY-MM-DD格式
     *
     * @return
     */
    public static String getDay() {
        return sdfDay.format(new Date());
    }

    /**
     * 获取YYYY-MM-DD HH格式
     *
     * @return
     */
    public static String getHour() {
        return sdfHour.format(new Date());
    }

    /**
     * 获取YYYY-MM-DD格式
     *
     * @return
     */
    public static String getDay(Date dt) {
        return sdfDay.format(dt);
    }

    /**
     * 获取YYYYMMDD格式
     *
     * @return
     */
    public static String getDays() {
        return sdfDays.format(new Date());
    }

    /**
     * 获取YYMM格式(年月格式)
     *
     * @return
     */
    public static String getYearMonth() {
        return sdfYearMonth.format(new Date());
    }

    public static String getLongYearMonth(Date date) {
        return sdfLongYearMonth.format(date);
    }

    /**
     * 获取YYYY-MM-DD HH:mm:ss格式
     *
     * @return
     */
    public static String getTime() {
        return sdfTime.format(new Date());
    }

    public static String getTime(Date date) {
        return sdfTime.format(date);
    }

    public static String getTimeYYYYMM(Date date) {
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM");
        return sdf2.format(date);
    }

    public static String getTime2() {
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMddHHmmss");
        return sdf2.format(new Date());
    }

    public static Date dayAdd(Date dtStart, int type, int num) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dtStart);
        calendar.add(type, num);
        return calendar.getTime();
    }

    public static boolean compareDate(String s, String e) {
        if (fomatDate(s) == null || fomatDate(e) == null) {
            return false;
        }
        return fomatDate(s).getTime() >= fomatDate(e).getTime();
    }

    public static boolean compareYYYYMM(String s, String e) {
        if (fomatDateYYYYMM(s) == null || fomatDateYYYYMM(e) == null) {
            return false;
        }
        return fomatDateYYYYMM(s).getTime() >= fomatDateYYYYMM(e).getTime();
    }

    /**
     * 格式化日期(YYYY-MM-DD HH)
     *
     * @return
     */
    public static Date fomatHour(String date) {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH");
        try {
            return fmt.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 格式化日期(YYYY-MM-DD)
     *
     * @return
     */
    public static Date fomatDate(String date) {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        try {
            return fmt.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 格式化日期2(YYYYMMDD)
     *
     * @return
     */
    public static Date fomatDate2(String date) {
        DateFormat fmt = new SimpleDateFormat("yyyyMMdd");
        try {
            return fmt.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Date fomatDateYYYYMM(String date) {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM");
        try {
            return fmt.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 格式化日期(yyyy-MM-dd HH:mm:ss)
     *
     * @return
     */
    public static Date fomatDatetime(String date) {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return fmt.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 格式化日期(yyyy-MM-dd HH:mm:ss.SSS)
     *
     * @return
     */
    public static Date fomatDatetimeWithMilli(String date) {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            return fmt.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 比较两个时间大小
     *
     * @param s
     * @param e
     * @return
     */
    public static boolean compareDateTimeWithMilli(String s, String e) {
        if (fomatDatetimeWithMilli(s) == null || fomatDatetimeWithMilli(e) == null) {
            return false;
        }
        return fomatDatetimeWithMilli(s).getTime() >= fomatDatetimeWithMilli(e).getTime();
    }

    public static boolean compareDateTime(String s, String e) {
        if (fomatDatetime(s) == null || fomatDatetime(e) == null) {
            return false;
        }
        return fomatDatetime(s).getTime() >= fomatDatetime(e).getTime();
    }

    public static boolean ifEqDateTime(String s, String e) {
        if (fomatDatetime(s) == null || fomatDatetime(e) == null) {
            return false;
        }
        return fomatDatetime(s).getTime() == fomatDatetime(e).getTime();
    }
    public static boolean ifEqHour(String s, String e) {
        if (fomatHour(s) == null || fomatHour(e) == null) {
            return false;
        }
        return fomatHour(s).getTime() == fomatHour(e).getTime();
    }

    public static boolean ifEqDate(String s, String e) {
        if (fomatDate(s) == null || fomatDate(e) == null) {
            return false;
        }
        return fomatDate(s).getTime() == fomatDate(e).getTime();
    }

    public static String milltoString(long oi) {
        return sdfTime.format(oi);
    }

    /**
     * 校验日期是否合法
     *
     * @return
     */
    public static boolean isValidDate(String s) {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        try {
            fmt.parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验日期时间是否合法
     *
     * @return
     */
    public static boolean isValidDatetime(String s) {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            fmt.parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static int getDiffYear(String startTime, String endTime) {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        try {
            int years = (int) (((fmt.parse(endTime).getTime() - fmt.parse(startTime).getTime()) / (1000 * 60 * 60 * 24))
                    / 365);
            return years;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * <li>功能描述：时间相减得到天数
     *
     * @param beginDateStr
     * @param endDateStr
     * @return long
     * @author Administrator
     */
    public static long getDaySub(String beginDateStr, String endDateStr) {
        long day = 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date beginDate = null;
        Date endDate = null;

        try {
            beginDate = format.parse(beginDateStr);
            endDate = format.parse(endDateStr);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        day = (endDate.getTime() - beginDate.getTime()) / (24 * 60 * 60 * 1000);

        return day;
    }

    public static long getHourSub(String beginDateStr, String endDateStr) {
        long day = 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH");
        Date beginDate = null;
        Date endDate = null;

        try {
            beginDate = format.parse(beginDateStr);
            endDate = format.parse(endDateStr);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        day = (endDate.getTime() - beginDate.getTime()) / (60 * 60 * 1000);
        return day;
    }

    public static long getSecondSub(String beginDateStr, String endDateStr) {
        long day = 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date beginDate = null;
        Date endDate = null;

        try {
            beginDate = format.parse(beginDateStr);
            endDate = format.parse(endDateStr);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        day = (endDate.getTime() - beginDate.getTime()) / 1000;
        return day;
    }

    /**
     * <li>功能描述：时间相减得到月数
     *
     * @param beginDateStr
     * @param endDateStr
     * @return long
     * @author Administrator
     */
    public static long getMonthSub(String beginDateStr, String endDateStr) throws Exception {
        long result = 0;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();

        c1.setTime(sdf.parse(beginDateStr));
        c2.setTime(sdf.parse(endDateStr));

        int y1 = c1.get(Calendar.YEAR);
        int y2 = c2.get(Calendar.YEAR);

        result = c2.get(Calendar.MONTH) - c1.get(Calendar.MONTH) + (y2 - y1) * 12;
        return Math.abs(result);
    }

    /**
     * 得到n天之后的日期
     *
     * @param days
     * @return
     */
    public static String getAfterDayDate(String days) {
        int daysInt = Integer.parseInt(days);

        Calendar canlendar = Calendar.getInstance(); // java.util包
        canlendar.add(Calendar.DATE, daysInt); // 日期减 如果不够减会将月变动
        Date date = canlendar.getTime();

        SimpleDateFormat sdfd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr = sdfd.format(date);

        return dateStr;
    }

    /**
     * 得到n天之后的日期
     *
     * @param d1
     * @param days
     * @param sdfd
     * @return
     */
    public static String getAfterDayDate(String d1, int days, SimpleDateFormat sdfd) throws Exception {
        int daysInt = days;
        Calendar canlendar = Calendar.getInstance(); // java.util包
        Date date = sdfd.parse(d1);
        canlendar.setTime(date);
        canlendar.add(Calendar.DATE, daysInt); // 日期减 如果不够减会将月变动
        Date date1 = canlendar.getTime();
        String dateStr = sdfd.format(date1);
        return dateStr;
    }

    /**
     * 格式化日期到想要的格式
     *
     * @param d1
     * @param sdfd
     * @return
     */
    public static String formatDayDate(String d1, SimpleDateFormat sdfd) throws Exception {
        Calendar canlendar = Calendar.getInstance(); // java.util包
        Date date = sdfd.parse(d1);
        canlendar.setTime(date);
        Date date1 = canlendar.getTime();
        String dateStr = sdfd.format(date1);
        return dateStr;
    }

    /**
     * 得到n月之后的日期
     *
     * @param months
     * @return
     */
    public static String getAfterMonthDate(String months) {
        int monthsInt = Integer.parseInt(months);

        Calendar canlendar = Calendar.getInstance(); // java.util包
        canlendar.add(Calendar.MONTH, monthsInt); // 日期减 如果不够减会将月变动
        Date date = canlendar.getTime();

        SimpleDateFormat sdfd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr = sdfd.format(date);

        return dateStr;
    }

    /**
     * 得到n年前的日期
     *
     * @param years
     * @return
     */
    public static Date getBeforeYearDate(int years, Date ndate) {
        int yearsInt = -years;

        Calendar canlendar = Calendar.getInstance(); // java.util包
        canlendar.setTime(ndate);
        canlendar.add(Calendar.YEAR, yearsInt);
        return canlendar.getTime();
    }

    /**
     * 得到n天之后是周几
     *
     * @param days
     * @return
     */
    public static String getAfterDayWeek(String days) {
        int daysInt = Integer.parseInt(days);

        Calendar canlendar = Calendar.getInstance(); // java.util包
        canlendar.add(Calendar.DATE, daysInt); // 日期减 如果不够减会将月变动
        Date date = canlendar.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("E");
        String dateStr = sdf.format(date);

        return dateStr;
    }

    /**
     * 根据当前时间得到频率字符串数组
     *
     * @return String[]
     */
    public static String[] getFrequencyStr() {
        String freqs[] = new String[4];
        Calendar now = Calendar.getInstance();
        String month = String.valueOf(now.get(Calendar.MONTH) + 1);
        int dayOfWeek = now.get(Calendar.DAY_OF_WEEK) - 1;
        String week = dayOfWeek == 0 ? "7" : String.valueOf(dayOfWeek);
        String day = String.valueOf(now.get(Calendar.DAY_OF_MONTH));
        String hour = String.valueOf(now.get(Calendar.HOUR_OF_DAY));

        // for every day
        freqs[0] = new StringBuilder().append("FT").append(hour).append("H").toString();
        // for every day of week
        freqs[1] = new StringBuilder().append("F").append(week).append("WT").append(hour).append("H").toString();
        // for every day of month
        freqs[2] = new StringBuilder().append("F").append(day).append("DT").append(hour).append("H").toString();
        // for every day of year
        freqs[3] = new StringBuilder().append("F").append(month).append("M").append(day).append("DT").append(hour)
                .append("H").toString();

        return freqs;
    }

    public static Date[] getDatePeriod(String datePeriodStr) {
        Date date[] = new Date[2];
        String regEx = "^P([PCMWD0-9]+?)\\-([PCMWD0-9]+?)$";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(datePeriodStr);
        m.find();
        String fromStr = m.group(1);
        String toStr = m.group(2);
        date[0] = parseDatePoint(fromStr);
        date[1] = parseDatePoint(toStr);

        return date;
    }

    private static Date parseDatePoint(String pointStr) {
        String regEx = "^(?:([P|C]{1})?(?:(\\d+|\\(\\w*\\))M)?(?:(\\d+|\\(\\w*\\))W)?(?:(\\d+|\\(\\w*\\))D)?)?$";

        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(pointStr);
        m.find();
        String pOrC = m.group(1);
        String month = m.group(2);
        String week = m.group(3);
        String day = m.group(4);

        Calendar now = Calendar.getInstance();
        if (month != null) {
            if (pOrC.equals("P")) {
                now.add(Calendar.YEAR, -1);
            }
            now.set(Calendar.MONDAY, Integer.valueOf(month) - 1);
        }
        if (week != null) {
            now.setFirstDayOfWeek(Calendar.MONDAY);
            if (pOrC.equals("P")) {
                now.add(Calendar.WEEK_OF_YEAR, -1);
            }
            now.set(Calendar.DAY_OF_WEEK, Integer.valueOf(week) + 1);
        }
        if (day != null) {
            if (pOrC.equals("P") && month == null) {
                now.add(Calendar.MONTH, -1);
            }
            now.set(Calendar.DAY_OF_MONTH, Integer.valueOf(day));
        }
        return now.getTime();
    }

    /*
     * 从身份证号解析生日信息
     */
    public static String parseDateFromIDCardNo(String idNum) throws Exception {
        int idLength = idNum.length();
        StringBuilder year = new StringBuilder();
        StringBuilder month = new StringBuilder();
        StringBuilder day = new StringBuilder();
        StringBuilder birthdateBuilder = new StringBuilder();
        if (idLength == 15) {
            year.append("19" + idNum.substring(6, 8));
            month.append(idNum.substring(8, 10));
            day.append(idNum.substring(10, 12));
            birthdateBuilder.append(year);
            birthdateBuilder.append("-");
            birthdateBuilder.append(month);
            birthdateBuilder.append("-");
            birthdateBuilder.append(day);
        } else if (idLength == 18) {
            year.append(idNum.substring(6, 10));
            month.append(idNum.substring(10, 12));
            day.append(idNum.substring(12, 14));
            birthdateBuilder.append(year);
            birthdateBuilder.append("-");
            birthdateBuilder.append(month);
            birthdateBuilder.append("-");
            birthdateBuilder.append(day);
        } else {
            throw new Exception("身份证号码错误");
        }
        return birthdateBuilder.toString();
    }

    /**
     * 根据出生日期计算年龄
     *
     * @param birthDate
     * @return
     * @throws ParseException
     */
    public static int getAgeByBirthDate(String birthDate) throws ParseException {
        Date bdate = sdfDay.parse(birthDate);
        Calendar calendar = new GregorianCalendar();
        Calendar nowCalendar = new GregorianCalendar();
        calendar.setTime(bdate);
        nowCalendar.setTime(new Date());
        int oyear = calendar.get(Calendar.YEAR);
        int nyear = nowCalendar.get(Calendar.YEAR);
        int tempYear = nyear - oyear;
        int omonth = calendar.get(Calendar.MONTH);
        int nmonth = nowCalendar.get(Calendar.MONTH);
        if (tempYear > 0) {
            if (nmonth < omonth) {
                tempYear -= 1;
            } else if (nmonth == omonth) {
                int day = calendar.get(Calendar.DATE);
                int nday = nowCalendar.get(Calendar.DATE);
                if (nday < day) {
                    tempYear -= 1;
                }
            }
        }
        return tempYear;
    }

    public static Map<String, Object> getYearAndMonth(String datestr) throws Exception {
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Object> map = new HashMap<>();
        Calendar c1 = Calendar.getInstance();
        c1.setTime(fmt.parse(datestr));
        int y1 = c1.get(Calendar.YEAR);
        map.put("YEAR", y1);
        int m1 = c1.get(Calendar.MONTH) + 1;
        map.put("MONTH", m1);
        return map;
    }

    public static String formatMonthOrDay(Object obj) {
        String formatter = "%02d";
        return String.format(formatter, obj);
    }

    public static String dateCompute(DateFormat srcfmt, DateFormat targetfmt, String dateStr) throws Exception {
        return dateCompute(srcfmt, targetfmt, dateStr, 0, 0, 0, 0, 0, 0);
    }

    public static String dateCompute(String dateStr, int interY, int interM, int interD) throws Exception {
        return dateCompute(sdfLongYearMonth, sdfLongYearMonth, dateStr, interY, interM, interD, 0, 0, 0);
    }

    public static String dateCompute(String dateStr, int interY, int interM) throws Exception {
        return dateCompute(sdfTime, sdfTime, dateStr, interY, interM, 0, 0, 0, 0);
    }

    public static String dateCompute(String dateStr, int interD) throws Exception {
        return dateCompute(sdfTime, sdfTime, dateStr, 0, 0, interD, 0, 0, 0);
    }

    public static String dateCompute(DateFormat srcfmt, DateFormat targetfmt, String dateStr, int interY, int interM,
                                     int interD, int interH, int interMi, int interS) throws Exception {
        Calendar c1 = Calendar.getInstance();
        c1.setTime(srcfmt.parse(dateStr));

        int y1 = c1.get(Calendar.YEAR);
        c1.set(Calendar.YEAR, y1 + interY);

        int m1 = c1.get(Calendar.MONTH);
        c1.set(Calendar.MONTH, m1 + interM);

        int d1 = c1.get(Calendar.DATE);
        c1.set(Calendar.DATE, d1 + interD);

        int h1 = c1.get(Calendar.HOUR);
        c1.set(Calendar.HOUR, h1 + interH);

        int mi1 = c1.get(Calendar.MINUTE);
        c1.set(Calendar.MINUTE, mi1 + interMi);

        int s1 = c1.get(Calendar.SECOND);
        c1.set(Calendar.SECOND, s1 + interS);

        Date date = c1.getTime();
        return targetfmt.format(date);
    }

    public static void main(String[] args) throws Exception {
        String curday = "";
        curday = "2018-10-26 14:29:26";
        curday = "2018-10";

        String fd = dateCompute(sdfLongYearMonth, sdfTime, curday, 0, 1, 0, 0, 0, -1);
        System.out.println(fd);
        System.out.println(compareYYYYMM("2018-10", "2018-11"));

        String datestr = getTime();
        Map<String, Object> map = getYearAndMonth(datestr);
        System.out.println("=========mmmmm=============" + datestr);
        System.out.println("=========mmmmm=============" + map.get("MONTH"));
        System.out.println(Integer.parseInt("09"));

        String lastMonth = DateUtil.dateCompute(DateUtil.getTime(), 0, -1);
        System.out.println(lastMonth);
    }

}
