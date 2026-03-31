package jp.co.oda32.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateTimeUtil {
    public static final String DATE_FORMAT_NORMAL = "yyyyMMdd";
    private static final String DATE_FORMAT = "yyyy/MM/dd";
    public static final String DATE_FORMAT_HAIFUN = "yyyy-MM-dd";
    private static final String JAPAN_DATE_FORMAT = "yyyy年MM月dd日";
    private static final String MONTH_FORMAT = "yyyy/MM";
    private static final String JAPAN_MONTH_FORMAT = "yyyy年MM月";
    private static final String DATE_TIME_FORMAT = "yyyy/MM/dd HH:mm:ss";
    public static final String TIMESTAMP_FORMAT = "yyyyMMddHHmmss";
    public static final String MySQL_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private DateTimeUtil() {}

    public static Timestamp getNow() {
        return new Timestamp(System.currentTimeMillis());
    }

    public static String getNowTimestampStr() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT));
    }

    public static LocalDate stringToLocalDate(final String date) {
        return isEmpty(date) ? null : LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_FORMAT));
    }

    public static LocalDate stringHaifunToLocalDate(final String date) {
        return isEmpty(date) ? null : LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_FORMAT_HAIFUN));
    }

    public static LocalDate stringToLocalDate(final String date, DateTimeFormatter formatter) {
        return isEmpty(date) ? null : LocalDate.parse(date, formatter);
    }

    public static LocalDateTime stringToLocalDateTimeForDateString(final String date) {
        return isEmpty(date) ? null : localDateToLocalDateTime(stringToLocalDate(date));
    }

    public static LocalDateTime stringToLocalDateTime(final String dateTime, DateTimeFormatter formatter) {
        return isEmpty(dateTime) ? null : LocalDateTime.parse(dateTime, formatter);
    }

    public static LocalDateTime stringToLocalDateTime(final String dateTime) {
        return isEmpty(dateTime) ? null : LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));
    }

    public static LocalDateTime localDateToLocalDateTime(LocalDate localDate) {
        return localDate == null ? null : localDate.atStartOfDay();
    }

    public static LocalDateTime stringMonthToLocalDateTime(String month) {
        return localDateToLocalDateTime(stringToLocalDate(String.format("%s/01", month)));
    }

    public static String localDateTimeToSlipDate(LocalDateTime localDateTime) {
        return localDateTime == null ? "" : localDateTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT_NORMAL));
    }

    public static String localDateToSlipDate(LocalDate localDate) {
        return localDate == null ? "" : localDate.format(DateTimeFormatter.ofPattern(DATE_FORMAT_NORMAL));
    }

    public static String localDateTimeToDateTimeStr(LocalDateTime localDateTime) {
        return localDateTime == null ? "" : localDateTime.format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));
    }

    public static String displayDate(LocalDateTime localDateTime) {
        return localDateTime == null ? "" : localDateTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
    }

    public static String displayDate(LocalDateTime localDateTime, DateTimeFormatter formatter) {
        return localDateTime == null ? "" : localDateTime.format(formatter);
    }

    public static String displayDate(LocalDate localDate) {
        return localDate == null ? "" : localDate.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
    }

    public static String displayDate(LocalDate localDate, DateTimeFormatter formatter) {
        return localDate == null ? "" : localDate.format(formatter);
    }

    public static String displayMonth(LocalDate localDate) {
        return localDate == null ? "" : localDate.format(DateTimeFormatter.ofPattern(MONTH_FORMAT));
    }

    public static String displayToday() {
        return displayDate(LocalDateTime.now());
    }

    public static String displayJpnDate(LocalDate localDate) {
        return localDate == null ? "" : localDate.format(DateTimeFormatter.ofPattern(JAPAN_DATE_FORMAT));
    }

    public static String displayJpnDate(String dateStr) {
        return isEmpty(dateStr) ? "" : displayJpnDate(stringToLocalDate(dateStr));
    }

    public static String displayJpnMonth(LocalDate localDate) {
        return localDate == null ? "" : localDate.format(DateTimeFormatter.ofPattern(JAPAN_MONTH_FORMAT));
    }

    public static LocalDate lastDayOfMonth(LocalDate localDate) {
        return localDate.withDayOfMonth(1).plusMonths(1).minusDays(1);
    }

    public static LocalDateTime lastDayOfMonth(LocalDateTime localDateTime) {
        return localDateTime.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1).plusMonths(1).minusSeconds(1);
    }

    public static boolean isRange(LocalDate checkDate, LocalDate from, LocalDate to) {
        if (checkDate == null) return false;
        if (from == null && to == null) return true;
        if (from == null) return checkDate.isBefore(to);
        if (to == null) return checkDate.isAfter(from) || checkDate.isEqual(from);
        return (checkDate.isAfter(from) || checkDate.isEqual(from)) && checkDate.isBefore(to);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
