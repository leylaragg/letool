package com.github.leyland.letool.tool.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/**
 * 日期时间工具类——基于 JDK 8+ {@link java.time} API.
 *
 * <p>所有方法均为空安全：传入 {@code null} 返回 {@code null} 或 0，不会抛出 NPE.</p>
 * <p>不需要再依赖 {@link java.text.SimpleDateFormat} 或 {@link java.util.Calendar}.</p>
 */
public final class DateUtil {

    private DateUtil() {}

    // ======================== 常用格式化器 ========================

    /** 标准日期格式：{@code yyyy-MM-dd} */
    public static final DateTimeFormatter STD_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 标准日期时间格式：{@code yyyy-MM-dd HH:mm:ss} */
    public static final DateTimeFormatter STD_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 日期+分钟格式：{@code yyyy-MM-dd HH:mm} */
    public static final DateTimeFormatter STD_DATE_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 紧凑日期格式：{@code yyyyMMdd} */
    public static final DateTimeFormatter PURE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 紧凑日期时间格式：{@code yyyyMMddHHmmss} */
    public static final DateTimeFormatter PURE_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 时间格式：{@code HH:mm:ss} */
    public static final DateTimeFormatter STD_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ======================== 当前时间 ========================

    /**
     * 当前日期（系统默认时区）.
     */
    public static LocalDate today() { return LocalDate.now(); }

    /**
     * 当前日期时间（系统默认时区）.
     */
    public static LocalDateTime now() { return LocalDateTime.now(); }

    // ======================== 格式化 ========================

    /**
     * 使用指定格式格式化日期.
     *
     * @param date      日期对象
     * @param formatter 格式化器
     * @return 格式化字符串，{@code date} 为 {@code null} 返回 {@code null}
     */
    public static String format(LocalDate date, DateTimeFormatter formatter) {
        return date == null ? null : date.format(formatter);
    }

    /**
     * 使用指定格式格式化日期时间.
     *
     * @param dt        日期时间对象
     * @param formatter 格式化器
     * @return 格式化字符串，{@code dt} 为 {@code null} 返回 {@code null}
     */
    public static String format(LocalDateTime dt, DateTimeFormatter formatter) {
        return dt == null ? null : dt.format(formatter);
    }

    /** 格式化为标准日期：{@code yyyy-MM-dd} */
    public static String formatDate(LocalDate date) { return format(date, STD_DATE); }

    /** 格式化为标准日期时间：{@code yyyy-MM-dd HH:mm:ss} */
    public static String formatDateTime(LocalDateTime dt) { return format(dt, STD_DATETIME); }

    /** 格式化为时间：{@code HH:mm:ss} */
    public static String formatTime(LocalDateTime dt) { return format(dt, STD_TIME); }

    // ======================== 解析 ========================

    /**
     * 以标准格式解析日期：{@code yyyy-MM-dd}.
     *
     * @param str 日期字符串
     * @return 解析结果，空白字符串返回 {@code null}
     */
    public static LocalDate parseDate(String str) {
        return parseDate(str, STD_DATE);
    }

    /**
     * 以指定格式解析日期.
     *
     * @param str       日期字符串
     * @param formatter 格式化器
     * @return 解析结果，空白字符串返回 {@code null}
     */
    public static LocalDate parseDate(String str, DateTimeFormatter formatter) {
        if (StrUtil.isBlank(str)) return null;
        return LocalDate.parse(str, formatter);
    }

    /**
     * 以标准格式解析日期时间：{@code yyyy-MM-dd HH:mm:ss}.
     */
    public static LocalDateTime parseDateTime(String str) {
        return parseDateTime(str, STD_DATETIME);
    }

    /**
     * 以指定格式解析日期时间.
     */
    public static LocalDateTime parseDateTime(String str, DateTimeFormatter formatter) {
        if (StrUtil.isBlank(str)) return null;
        return LocalDateTime.parse(str, formatter);
    }

    // ======================== Date ⇄ LocalDateTime 转换 ========================

    /**
     * {@link java.util.Date} → {@link LocalDateTime}（系统默认时区）.
     *
     * @param date 旧 Date 对象
     * @return 对应的 LocalDateTime，{@code null} 返回 {@code null}
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * {@link LocalDateTime} → {@link java.util.Date}（系统默认时区）.
     *
     * @param dt 日期时间对象
     * @return 对应的 Date，{@code null} 返回 {@code null}
     */
    public static Date toDate(LocalDateTime dt) {
        if (dt == null) return null;
        return Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ======================== 差值计算 ========================

    /**
     * 两个日期之间的天数.
     *
     * @param start 起始日期
     * @param end   结束日期
     * @return 间隔天数，任一为 {@code null} 返回 0
     */
    public static long betweenDays(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 两个日期时间之间的小时数.
     */
    public static long betweenHours(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return ChronoUnit.HOURS.between(start, end);
    }

    /**
     * 两个日期时间之间的分钟数.
     */
    public static long betweenMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return ChronoUnit.MINUTES.between(start, end);
    }

    // ======================== 时间偏移 ========================

    /**
     * 日期时间加天数.
     *
     * @param dt   日期时间
     * @param days 天数（可为负）
     * @return 偏移后的日期时间
     */
    public static LocalDateTime plusDays(LocalDateTime dt, long days) {
        return dt == null ? null : dt.plusDays(days);
    }

    /**
     * 日期时间加小时.
     */
    public static LocalDateTime plusHours(LocalDateTime dt, long hours) {
        return dt == null ? null : dt.plusHours(hours);
    }

    /**
     * 日期时间加分钟.
     */
    public static LocalDateTime plusMinutes(LocalDateTime dt, long minutes) {
        return dt == null ? null : dt.plusMinutes(minutes);
    }

    // ======================== 时间边界 ========================

    /**
     * 当天开始时刻：{@code 00:00:00}.
     */
    public static LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /**
     * 当天结束时刻：{@code 23:59:59}.
     *
     * <p>注意：毫秒为 000，如需精确到纳秒请使用 {@link LocalDate#atTime(LocalTime)} 直接指定.</p>
     */
    public static LocalDateTime endOfDay(LocalDate date) {
        return date == null ? null : date.atTime(23, 59, 59);
    }

    /**
     * 当月第一天.
     */
    public static LocalDate firstDayOfMonth(LocalDate date) {
        return date == null ? null : date.with(TemporalAdjusters.firstDayOfMonth());
    }

    /**
     * 当月最后一天.
     */
    public static LocalDate lastDayOfMonth(LocalDate date) {
        return date == null ? null : date.with(TemporalAdjusters.lastDayOfMonth());
    }

    // ======================== 时间戳 ========================

    /**
     * 转为毫秒时间戳（系统默认时区）.
     *
     * @param dt 日期时间
     * @return 毫秒时间戳，{@code null} 返回 0
     */
    public static long toEpochMilli(LocalDateTime dt) {
        return dt == null ? 0 : dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 转为秒时间戳（系统默认时区）.
     */
    public static long toEpochSecond(LocalDateTime dt) {
        return dt == null ? 0 : dt.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /**
     * 从毫秒时间戳恢复为 LocalDateTime（系统默认时区）.
     */
    public static LocalDateTime ofEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
