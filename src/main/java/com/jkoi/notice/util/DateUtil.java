package com.jkoi.notice.util;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期工具类，基于 java.time API。
 */
public final class DateUtil {

    public static final String FORMAT_DATE_COMPACT = "yyyyMMdd";
    public static final String FORMAT_DATE_COMPACT_TILL_YEAR = "yyyy";
    public static final String FORMAT_DATE_NORMAL = "yyyy-MM-dd";
    public static final String FORMAT_DATETIME_COMPACT = "yyyyMMddHHmmss";
    public static final String FORMAT_DATETIME_COMPACT_DAY = "yyyyMMdd";
    public static final String FORMAT_DATETIME_COMPACT_SPACE = "yyyyMMdd HHmmss";
    public static final String FORMAT_DATETIME_NORMAL = "yyyy-MM-dd HH:mm:ss";
    public static final String FORMAT_DATETIME_TILL_MINUTE = "yyyy-MM-dd HH:mm";
    public static final String FORMAT_TIMESTAMP_NORMAL = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String FORMAT_DATE_COMPACT_TILL_MONTH = "yyyyMM";
    public static final String FORMAT_DATE_NORMAL_TILL_MONTH = "yyyy-MM";
    public static final String FORMAT_TIME_COMPACT = "HHmmss";
    public static final String FORMAT_TIME_COMPACT_TILL_MINUTE = "HH:mm";
    public static final String FORMAT_DATETIME_COMPACT_MINUTE = "yyyyMMddHHmm";

    private DateUtil() {
    }

    public static Date getNow() {
        return new Date();
    }

    public static String getCurrentTime(String format) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }

    public static long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static Integer getYear(Date date) {
        return toLocalDate(date).getYear();
    }

    public static Integer getMonth(Date date) {
        return toLocalDate(date).getMonthValue();
    }

    public static Integer getDayOfMonth(Date date) {
        return toLocalDate(date).getDayOfMonth();
    }

    public static Date parseDate(String dateStr, String formatPattern) {
        if (StringUtils.isEmpty(dateStr)) {
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(formatPattern));
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date getNowAtStart() {
        return toDate(LocalDate.now().atStartOfDay());
    }

    public static Date getTomorrowAtStart() {
        return toDate(LocalDate.now().plusDays(1).atStartOfDay());
    }

    public static Date getYesterdayAtStart() {
        return toDate(LocalDate.now().minusDays(1).atStartOfDay());
    }

    public static Date plusYears(Date date, int years) {
        return toDate(toLocalDateTime(date).plusYears(years));
    }

    public static Date plusMonths(Date date, int months) {
        return toDate(toLocalDateTime(date).plusMonths(months));
    }

    public static Date plusDays(Date date, int days) {
        return toDate(toLocalDateTime(date).plusDays(days));
    }

    public static Date plusHours(Date date, int hours) {
        return toDate(toLocalDateTime(date).plusHours(hours));
    }

    public static Date plusMinutes(Date date, int minutes) {
        return toDate(toLocalDateTime(date).plusMinutes(minutes));
    }

    public static Date plusSeconds(Date date, int seconds) {
        return toDate(toLocalDateTime(date).plusSeconds(seconds));
    }

    public static String formatDate(Date date, String formatPattern) {
        return toLocalDateTime(date).format(DateTimeFormatter.ofPattern(formatPattern));
    }

    public static String formatNow(String formatPattern) {
        return formatDate(getNow(), formatPattern);
    }

    public boolean checkDateFormat(String value, String format) {
        try {
            parseDate(value, format);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ======================== 内部转换 ========================

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
