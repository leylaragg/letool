package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.date.DateOperationException;
import io.github.leylaragg.letool.tool.date.DateTimeRange;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.IsoChronology;
import java.time.chrono.IsoEra;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 基于 JDK 17 {@code java.time} 的严格日期时间工具类。
 *
 * <p>日期格式器不可变且线程安全，可以在并发业务中复用。除 {@code tryParse} 容错入口外，
 * 所有参数均为必填；空值、非法文本、格式化失败和转换失败会抛出带稳定错误码的
 * {@link DateOperationException}，不再静默返回 {@code null} 或 {@code 0}。</p>
 *
 * <p>不带时区的 Date 和时间戳转换方法使用系统默认时区，适合本地便捷调用。跨系统存储、
 * 消息传输和多时区业务应优先使用带 {@link ZoneId} 的重载。</p>
 */
public final class DateUtil {

    /** 标准日期格式：{@code uuuu-MM-dd}。 */
    public static final DateTimeFormatter STD_DATE = formatter("uuuu-MM-dd");

    /** 标准日期时间格式：{@code uuuu-MM-dd HH:mm:ss}。 */
    public static final DateTimeFormatter STD_DATETIME = formatter("uuuu-MM-dd HH:mm:ss");

    /** 日期分钟格式：{@code uuuu-MM-dd HH:mm}。 */
    public static final DateTimeFormatter STD_DATE_MINUTE = formatter("uuuu-MM-dd HH:mm");

    /** 标准年月格式：{@code uuuu-MM}。 */
    public static final DateTimeFormatter STD_MONTH = formatter("uuuu-MM");

    /** 毫秒日期时间格式：{@code uuuu-MM-dd HH:mm:ss.SSS}。 */
    public static final DateTimeFormatter STD_DATETIME_MILLI = formatter("uuuu-MM-dd HH:mm:ss.SSS");

    /** 紧凑日期格式：{@code uuuuMMdd}。 */
    public static final DateTimeFormatter PURE_DATE = formatter("uuuuMMdd");

    /** 紧凑日期时间格式：{@code uuuuMMddHHmmss}。 */
    public static final DateTimeFormatter PURE_DATETIME = formatter("uuuuMMddHHmmss");

    /** 紧凑时间格式：{@code HHmmss}。 */
    public static final DateTimeFormatter PURE_TIME = formatter("HHmmss");

    /** 紧凑毫秒日期时间格式：{@code uuuuMMddHHmmssSSS}。 */
    public static final DateTimeFormatter PURE_DATETIME_MILLI = formatter("uuuuMMddHHmmssSSS");

    /** 标准时间格式：{@code HH:mm:ss}。 */
    public static final DateTimeFormatter STD_TIME = formatter("HH:mm:ss");

    /** 工具类不允许实例化。 */
    private DateUtil() {
    }

    /**
     * 获取系统默认时区下的当前日期。
     *
     * @return 当前日期
     */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /**
     * 使用指定时钟获取当前日期。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下的当前日期
     * @throws DateOperationException 时钟为空时抛出
     */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(requireArgument(clock, "clock"));
    }

    /**
     * 获取系统默认时区下的当前日期时间。
     *
     * @return 当前日期时间
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 使用指定时钟获取当前日期时间。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下的当前日期时间
     * @throws DateOperationException 时钟为空时抛出
     */
    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(requireArgument(clock, "clock"));
    }

    /**
     * 获取系统默认时区下的当前年份。
     *
     * @return 当前年份
     */
    public static int currentYear() {
        return currentYear(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取当前年份。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下的当前年份
     * @throws DateOperationException 时钟为空时抛出
     */
    public static int currentYear(Clock clock) {
        return year(now(clock));
    }

    /**
     * 获取系统默认时区下的当前月份。
     *
     * @return 当前月份，范围为 1 到 12
     */
    public static int currentMonth() {
        return currentMonth(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取当前月份。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下的月份，范围为 1 到 12
     * @throws DateOperationException 时钟为空时抛出
     */
    public static int currentMonth(Clock clock) {
        return month(now(clock));
    }

    /**
     * 获取系统默认时区下的当前月份枚举。
     *
     * @return 当前月份枚举
     */
    public static Month currentMonthEnum() {
        return currentMonthEnum(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取当前月份枚举。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下的月份枚举
     * @throws DateOperationException 时钟为空时抛出
     */
    public static Month currentMonthEnum(Clock clock) {
        return now(clock).getMonth();
    }

    /**
     * 获取系统默认时区下当前月份中的日期。
     *
     * @return 当前月份中的日期，范围为 1 到 31
     */
    public static int currentDay() {
        return currentDay(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取当前月份中的日期。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下当前月份中的日期
     * @throws DateOperationException 时钟为空时抛出
     */
    public static int currentDay(Clock clock) {
        return day(now(clock));
    }

    /**
     * 获取系统默认时区下的当前小时。
     *
     * @return 当前小时，范围为 0 到 23
     */
    public static int currentHour() {
        return currentHour(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取当前小时。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下的小时，范围为 0 到 23
     * @throws DateOperationException 时钟为空时抛出
     */
    public static int currentHour(Clock clock) {
        return hour(now(clock));
    }

    /**
     * 获取系统默认时区下的当前分钟。
     *
     * @return 当前分钟，范围为 0 到 59
     */
    public static int currentMinute() {
        return currentMinute(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取当前分钟。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下的分钟，范围为 0 到 59
     * @throws DateOperationException 时钟为空时抛出
     */
    public static int currentMinute(Clock clock) {
        return minute(now(clock));
    }

    /**
     * 获取系统默认时区下的当前秒数。
     *
     * @return 当前秒数，范围为 0 到 59
     */
    public static int currentSecond() {
        return currentSecond(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取当前秒数。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return 指定时钟下的秒数，范围为 0 到 59
     * @throws DateOperationException 时钟为空时抛出
     */
    public static int currentSecond(Clock clock) {
        return second(now(clock));
    }

    /**
     * 获取系统默认时区下的标准当前日期文本。
     *
     * @return {@code uuuu-MM-dd} 格式的当前日期
     */
    public static String todayText() {
        return todayText(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取标准当前日期文本。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return {@code uuuu-MM-dd} 格式的当前日期
     * @throws DateOperationException 时钟为空时抛出
     */
    public static String todayText(Clock clock) {
        return formatDate(today(clock));
    }

    /**
     * 获取系统默认时区下的标准当前日期时间文本。
     *
     * @return {@code uuuu-MM-dd HH:mm:ss} 格式的当前日期时间
     */
    public static String nowText() {
        return nowText(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟获取标准当前日期时间文本。
     *
     * @param clock 提供当前时刻和时区的时钟
     * @return {@code uuuu-MM-dd HH:mm:ss} 格式的当前日期时间
     * @throws DateOperationException 时钟为空时抛出
     */
    public static String nowText(Clock clock) {
        return formatDateTime(now(clock));
    }

    /**
     * 获取日期时间对象的年份。
     *
     * @param temporal 支持年份字段的日期时间对象
     * @return 年份
     * @throws DateOperationException 参数为空或对象不支持年份字段时抛出
     */
    public static int year(TemporalAccessor temporal) {
        return temporalField(temporal, ChronoField.YEAR, "year");
    }

    /**
     * 获取日期时间对象的月份。
     *
     * @param temporal 支持月份字段的日期时间对象
     * @return 月份，范围为 1 到 12
     * @throws DateOperationException 参数为空或对象不支持月份字段时抛出
     */
    public static int month(TemporalAccessor temporal) {
        return temporalField(temporal, ChronoField.MONTH_OF_YEAR, "month");
    }

    /**
     * 获取日期时间对象的月份枚举。
     *
     * @param temporal 支持月份字段的日期时间对象
     * @return 月份枚举
     * @throws DateOperationException 参数为空或对象不支持月份字段时抛出
     */
    public static Month monthEnum(TemporalAccessor temporal) {
        return Month.of(month(temporal));
    }

    /**
     * 获取日期时间对象在当月中的日期。
     *
     * @param temporal 支持日字段的日期时间对象
     * @return 当月中的日期
     * @throws DateOperationException 参数为空或对象不支持日字段时抛出
     */
    public static int day(TemporalAccessor temporal) {
        return temporalField(temporal, ChronoField.DAY_OF_MONTH, "day");
    }

    /**
     * 获取日期时间对象的小时。
     *
     * @param temporal 支持小时字段的日期时间对象
     * @return 小时，范围为 0 到 23
     * @throws DateOperationException 参数为空或对象不支持小时字段时抛出
     */
    public static int hour(TemporalAccessor temporal) {
        return temporalField(temporal, ChronoField.HOUR_OF_DAY, "hour");
    }

    /**
     * 获取日期时间对象的分钟。
     *
     * @param temporal 支持分钟字段的日期时间对象
     * @return 分钟，范围为 0 到 59
     * @throws DateOperationException 参数为空或对象不支持分钟字段时抛出
     */
    public static int minute(TemporalAccessor temporal) {
        return temporalField(temporal, ChronoField.MINUTE_OF_HOUR, "minute");
    }

    /**
     * 获取日期时间对象的秒数。
     *
     * @param temporal 支持秒字段的日期时间对象
     * @return 秒数，范围为 0 到 59
     * @throws DateOperationException 参数为空或对象不支持秒字段时抛出
     */
    public static int second(TemporalAccessor temporal) {
        return temporalField(temporal, ChronoField.SECOND_OF_MINUTE, "second");
    }

    /**
     * 使用系统默认时区获取旧版 Date 的年份。
     *
     * @param date 旧版 Date 对象
     * @return 系统默认时区下的年份
     * @throws DateOperationException Date 为空或转换失败时抛出
     */
    public static int year(Date date) {
        return year(date, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区获取旧版 Date 的年份。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标时区
     * @return 指定时区下的年份
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static int year(Date date, ZoneId zoneId) {
        return year(toZonedDateTime(date, zoneId));
    }

    /**
     * 使用系统默认时区获取旧版 Date 的月份。
     *
     * @param date 旧版 Date 对象
     * @return 系统默认时区下的月份，范围为 1 到 12
     * @throws DateOperationException Date 为空或转换失败时抛出
     */
    public static int month(Date date) {
        return month(date, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区获取旧版 Date 的月份。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标时区
     * @return 指定时区下的月份，范围为 1 到 12
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static int month(Date date, ZoneId zoneId) {
        return month(toZonedDateTime(date, zoneId));
    }

    /**
     * 使用系统默认时区获取旧版 Date 的月份枚举。
     *
     * @param date 旧版 Date 对象
     * @return 系统默认时区下的月份枚举
     * @throws DateOperationException Date 为空或转换失败时抛出
     */
    public static Month monthEnum(Date date) {
        return monthEnum(date, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区获取旧版 Date 的月份枚举。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标时区
     * @return 指定时区下的月份枚举
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static Month monthEnum(Date date, ZoneId zoneId) {
        return monthEnum(toZonedDateTime(date, zoneId));
    }

    /**
     * 使用系统默认时区获取旧版 Date 在当月中的日期。
     *
     * @param date 旧版 Date 对象
     * @return 系统默认时区下当月中的日期
     * @throws DateOperationException Date 为空或转换失败时抛出
     */
    public static int day(Date date) {
        return day(date, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区获取旧版 Date 在当月中的日期。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标时区
     * @return 指定时区下当月中的日期
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static int day(Date date, ZoneId zoneId) {
        return day(toZonedDateTime(date, zoneId));
    }

    /**
     * 使用系统默认时区获取旧版 Date 的小时。
     *
     * @param date 旧版 Date 对象
     * @return 系统默认时区下的小时
     * @throws DateOperationException Date 为空或转换失败时抛出
     */
    public static int hour(Date date) {
        return hour(date, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区获取旧版 Date 的小时。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标时区
     * @return 指定时区下的小时
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static int hour(Date date, ZoneId zoneId) {
        return hour(toZonedDateTime(date, zoneId));
    }

    /**
     * 使用系统默认时区获取旧版 Date 的分钟。
     *
     * @param date 旧版 Date 对象
     * @return 系统默认时区下的分钟
     * @throws DateOperationException Date 为空或转换失败时抛出
     */
    public static int minute(Date date) {
        return minute(date, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区获取旧版 Date 的分钟。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标时区
     * @return 指定时区下的分钟
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static int minute(Date date, ZoneId zoneId) {
        return minute(toZonedDateTime(date, zoneId));
    }

    /**
     * 使用系统默认时区获取旧版 Date 的秒数。
     *
     * @param date 旧版 Date 对象
     * @return 系统默认时区下的秒数
     * @throws DateOperationException Date 为空或转换失败时抛出
     */
    public static int second(Date date) {
        return second(date, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区获取旧版 Date 的秒数。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标时区
     * @return 指定时区下的秒数
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static int second(Date date, ZoneId zoneId) {
        return second(toZonedDateTime(date, zoneId));
    }

    /**
     * 使用指定格式化器格式化日期。
     *
     * @param date 日期
     * @param formatter 日期格式化器
     * @return 格式化文本
     * @throws DateOperationException 参数为空或格式化失败时抛出
     */
    public static String format(LocalDate date, DateTimeFormatter formatter) {
        return formatTemporal(date, formatter);
    }

    /**
     * 使用指定格式化器格式化日期时间。
     *
     * @param dateTime 日期时间
     * @param formatter 日期时间格式化器
     * @return 格式化文本
     * @throws DateOperationException 参数为空或格式化失败时抛出
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        return formatTemporal(dateTime, formatter);
    }

    /**
     * 使用指定格式化器输出任意受支持的日期时间对象。
     *
     * @param temporal 日期时间对象
     * @param formatter 日期时间格式化器
     * @return 格式化文本
     * @throws DateOperationException 参数为空或对象不包含格式所需字段时抛出
     */
    public static String format(TemporalAccessor temporal, DateTimeFormatter formatter) {
        return formatTemporal(temporal, formatter);
    }

    /**
     * 使用自定义格式输出日期时间对象。
     *
     * @param temporal 日期时间对象
     * @param pattern 日期时间格式
     * @return 格式化文本
     * @throws DateOperationException 参数为空、格式非法或对象不包含所需字段时抛出
     */
    public static String format(TemporalAccessor temporal, String pattern) {
        return format(temporal, formatter(pattern));
    }

    /**
     * 使用系统默认时区和指定格式化器输出旧版 Date。
     *
     * @param date 旧版 Date 对象
     * @param formatter 日期时间格式化器
     * @return 系统默认时区下的格式化文本
     * @throws DateOperationException 参数为空或格式化失败时抛出
     */
    public static String format(Date date, DateTimeFormatter formatter) {
        return format(date, formatter, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区和格式化器输出旧版 Date。
     *
     * @param date 旧版 Date 对象
     * @param formatter 日期时间格式化器
     * @param zoneId 目标时区
     * @return 指定时区下的格式化文本
     * @throws DateOperationException 参数为空、转换失败或格式化失败时抛出
     */
    public static String format(Date date, DateTimeFormatter formatter, ZoneId zoneId) {
        ZonedDateTime dateTime = toZonedDateTime(date, zoneId);
        DateTimeFormatter zonedFormatter = requireArgument(formatter, "formatter")
                .withZone(dateTime.getZone());
        return format(dateTime, zonedFormatter);
    }

    /**
     * 使用系统默认时区和自定义格式输出旧版 Date。
     *
     * @param date 旧版 Date 对象
     * @param pattern 日期时间格式
     * @return 系统默认时区下的格式化文本
     * @throws DateOperationException 参数为空、格式非法或格式化失败时抛出
     */
    public static String format(Date date, String pattern) {
        return format(date, formatter(pattern), ZoneId.systemDefault());
    }

    /**
     * 使用指定时区和自定义格式输出旧版 Date。
     *
     * @param date 旧版 Date 对象
     * @param pattern 日期时间格式
     * @param zoneId 目标时区
     * @return 指定时区下的格式化文本
     * @throws DateOperationException 参数为空、格式非法、转换失败或格式化失败时抛出
     */
    public static String format(Date date, String pattern, ZoneId zoneId) {
        return format(date, formatter(pattern), zoneId);
    }

    /**
     * 将日期格式化为标准日期文本。
     *
     * @param date 日期
     * @return {@code uuuu-MM-dd} 文本
     * @throws DateOperationException 日期为空时抛出
     */
    public static String formatDate(LocalDate date) {
        return format(date, STD_DATE);
    }

    /**
     * 将日期时间格式化为标准日期时间文本。
     *
     * @param dateTime 日期时间
     * @return {@code uuuu-MM-dd HH:mm:ss} 文本
     * @throws DateOperationException 日期时间为空时抛出
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return format(dateTime, STD_DATETIME);
    }

    /**
     * 从日期时间中格式化标准时间文本。
     *
     * @param dateTime 日期时间
     * @return {@code HH:mm:ss} 文本
     * @throws DateOperationException 日期时间为空时抛出
     */
    public static String formatTime(LocalDateTime dateTime) {
        return format(dateTime, STD_TIME);
    }

    /**
     * 将本地时间格式化为标准时间文本。
     *
     * @param time 本地时间
     * @return {@code HH:mm:ss} 文本
     * @throws DateOperationException 本地时间为空时抛出
     */
    public static String formatTime(LocalTime time) {
        return formatTemporal(time, STD_TIME);
    }

    /**
     * 使用标准格式严格解析日期。
     *
     * @param text 日期文本
     * @return 解析后的日期
     * @throws DateOperationException 文本为空白或日期非法时抛出
     */
    public static LocalDate parseDate(String text) {
        return parseDate(text, STD_DATE);
    }

    /**
     * 使用指定格式化器严格解析日期。
     *
     * @param text 日期文本
     * @param formatter 日期格式化器
     * @return 解析后的日期
     * @throws DateOperationException 参数为空或日期非法时抛出
     */
    public static LocalDate parseDate(String text, DateTimeFormatter formatter) {
        return parseRequired(text, formatter, "dateText", LocalDate::parse);
    }

    /**
     * 使用自定义格式严格解析日期。
     *
     * @param text 日期文本
     * @param pattern 日期格式
     * @return 解析后的日期
     * @throws DateOperationException 参数为空、格式非法或日期非法时抛出
     */
    public static LocalDate parseDate(String text, String pattern) {
        return parseDate(text, formatter(pattern));
    }

    /**
     * 尝试使用标准格式严格解析日期。
     *
     * @param text 日期文本，空白或非法输入返回空结果
     * @return 解析成功时返回日期，否则返回空结果
     */
    public static Optional<LocalDate> tryParseDate(String text) {
        return tryParseDate(text, STD_DATE);
    }

    /**
     * 尝试使用指定格式化器严格解析日期。
     *
     * @param text 日期文本，空白或非法输入返回空结果
     * @param formatter 日期格式化器
     * @return 解析成功时返回日期，否则返回空结果
     * @throws DateOperationException 格式化器为空时抛出
     */
    public static Optional<LocalDate> tryParseDate(String text, DateTimeFormatter formatter) {
        return tryParse(text, formatter, LocalDate::parse);
    }

    /**
     * 尝试使用自定义格式严格解析日期。
     *
     * @param text 日期文本，空白或非法输入返回空结果
     * @param pattern 日期格式
     * @return 解析成功时返回日期，否则返回空结果
     * @throws DateOperationException pattern 为空或格式非法时抛出
     */
    public static Optional<LocalDate> tryParseDate(String text, String pattern) {
        return tryParseDate(text, formatter(pattern));
    }

    /**
     * 使用标准格式严格解析日期时间。
     *
     * @param text 日期时间文本
     * @return 解析后的日期时间
     * @throws DateOperationException 文本为空白或日期时间非法时抛出
     */
    public static LocalDateTime parseDateTime(String text) {
        return parseDateTime(text, STD_DATETIME);
    }

    /**
     * 使用指定格式化器严格解析日期时间。
     *
     * @param text 日期时间文本
     * @param formatter 日期时间格式化器
     * @return 解析后的日期时间
     * @throws DateOperationException 参数为空或日期时间非法时抛出
     */
    public static LocalDateTime parseDateTime(String text, DateTimeFormatter formatter) {
        return parseRequired(text, formatter, "dateTimeText", LocalDateTime::parse);
    }

    /**
     * 使用自定义格式严格解析日期时间。
     *
     * @param text 日期时间文本
     * @param pattern 日期时间格式
     * @return 解析后的日期时间
     * @throws DateOperationException 参数为空、格式非法或日期时间非法时抛出
     */
    public static LocalDateTime parseDateTime(String text, String pattern) {
        return parseDateTime(text, formatter(pattern));
    }

    /**
     * 尝试使用标准格式严格解析日期时间。
     *
     * @param text 日期时间文本，空白或非法输入返回空结果
     * @return 解析成功时返回日期时间，否则返回空结果
     */
    public static Optional<LocalDateTime> tryParseDateTime(String text) {
        return tryParseDateTime(text, STD_DATETIME);
    }

    /**
     * 尝试使用指定格式化器严格解析日期时间。
     *
     * @param text 日期时间文本，空白或非法输入返回空结果
     * @param formatter 日期时间格式化器
     * @return 解析成功时返回日期时间，否则返回空结果
     * @throws DateOperationException 格式化器为空时抛出
     */
    public static Optional<LocalDateTime> tryParseDateTime(String text, DateTimeFormatter formatter) {
        return tryParse(text, formatter, LocalDateTime::parse);
    }

    /**
     * 尝试使用自定义格式严格解析日期时间。
     *
     * @param text 日期时间文本，空白或非法输入返回空结果
     * @param pattern 日期时间格式
     * @return 解析成功时返回日期时间，否则返回空结果
     * @throws DateOperationException pattern 为空或格式非法时抛出
     */
    public static Optional<LocalDateTime> tryParseDateTime(String text, String pattern) {
        return tryParseDateTime(text, formatter(pattern));
    }

    /**
     * 使用标准时间格式严格解析本地时间。
     *
     * @param text 时间文本
     * @return 解析后的本地时间
     * @throws DateOperationException 文本为空白或时间非法时抛出
     */
    public static LocalTime parseTime(String text) {
        return parseTime(text, STD_TIME);
    }

    /**
     * 使用指定格式化器严格解析本地时间。
     *
     * @param text 时间文本
     * @param formatter 时间格式化器
     * @return 解析后的本地时间
     * @throws DateOperationException 参数为空或时间非法时抛出
     */
    public static LocalTime parseTime(String text, DateTimeFormatter formatter) {
        return parseRequired(text, formatter, "timeText", LocalTime::parse);
    }

    /**
     * 使用自定义格式严格解析本地时间。
     *
     * @param text 时间文本
     * @param pattern 时间格式
     * @return 解析后的本地时间
     * @throws DateOperationException 参数为空、格式非法或时间非法时抛出
     */
    public static LocalTime parseTime(String text, String pattern) {
        return parseTime(text, formatter(pattern));
    }

    /**
     * 尝试使用标准时间格式严格解析本地时间。
     *
     * @param text 时间文本，空白或非法输入返回空结果
     * @return 解析成功时返回本地时间，否则返回空结果
     */
    public static Optional<LocalTime> tryParseTime(String text) {
        return tryParseTime(text, STD_TIME);
    }

    /**
     * 尝试使用指定格式化器严格解析本地时间。
     *
     * @param text 时间文本，空白或非法输入返回空结果
     * @param formatter 时间格式化器
     * @return 解析成功时返回本地时间，否则返回空结果
     * @throws DateOperationException 格式化器为空时抛出
     */
    public static Optional<LocalTime> tryParseTime(String text, DateTimeFormatter formatter) {
        return tryParse(text, formatter, LocalTime::parse);
    }

    /**
     * 尝试使用自定义格式严格解析本地时间。
     *
     * @param text 时间文本，空白或非法输入返回空结果
     * @param pattern 时间格式
     * @return 解析成功时返回本地时间，否则返回空结果
     * @throws DateOperationException pattern 为空或格式非法时抛出
     */
    public static Optional<LocalTime> tryParseTime(String text, String pattern) {
        return tryParseTime(text, formatter(pattern));
    }

    /**
     * 使用系统默认时区将旧版 Date 转换为 LocalDateTime。
     *
     * @param date 旧版 Date 对象
     * @return 系统默认时区下的本地日期时间
     * @throws DateOperationException Date 为空或转换失败时抛出
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        return toLocalDateTime(date, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区将旧版 Date 转换为 LocalDateTime。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标本地时区
     * @return 指定时区下的本地日期时间
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static LocalDateTime toLocalDateTime(Date date, ZoneId zoneId) {
        return executeConversion(() -> LocalDateTime.ofInstant(
                requireArgument(date, "date").toInstant(),
                requireArgument(zoneId, "zoneId")
        ));
    }

    /**
     * 使用指定时区将 Instant 转换为 LocalDateTime。
     *
     * @param instant 绝对时刻
     * @param zoneId 目标本地时区
     * @return 指定时区下的本地日期时间
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static LocalDateTime toLocalDateTime(Instant instant, ZoneId zoneId) {
        return executeConversion(() -> LocalDateTime.ofInstant(
                requireArgument(instant, "instant"),
                requireArgument(zoneId, "zoneId")
        ));
    }

    /**
     * 使用系统默认时区将 LocalDateTime 转换为旧版 Date。
     *
     * @param dateTime 本地日期时间
     * @return 表示同一绝对时刻的 Date
     * @throws DateOperationException 日期时间为空或转换失败时抛出
     */
    public static Date toDate(LocalDateTime dateTime) {
        return toDate(dateTime, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区将 LocalDateTime 转换为旧版 Date。
     *
     * @param dateTime 本地日期时间
     * @param zoneId 本地日期时间所属时区
     * @return 表示同一绝对时刻的 Date
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static Date toDate(LocalDateTime dateTime, ZoneId zoneId) {
        return executeConversion(() -> Date.from(toInstant(dateTime, zoneId)));
    }

    /**
     * 使用指定时区将 LocalDateTime 转换为 Instant。
     *
     * @param dateTime 本地日期时间
     * @param zoneId 本地日期时间所属时区
     * @return 表示绝对时刻的 Instant
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static Instant toInstant(LocalDateTime dateTime, ZoneId zoneId) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime")
                .atZone(requireArgument(zoneId, "zoneId"))
                .toInstant());
    }

    /**
     * 使用指定时区将 LocalDateTime 转换为 ZonedDateTime。
     *
     * @param dateTime 本地日期时间
     * @param zoneId 本地日期时间所属时区
     * @return 带完整时区规则的日期时间
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static ZonedDateTime toZonedDateTime(LocalDateTime dateTime, ZoneId zoneId) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime")
                .atZone(requireArgument(zoneId, "zoneId")));
    }

    /**
     * 使用指定时区将 LocalDateTime 转换为 OffsetDateTime。
     *
     * @param dateTime 本地日期时间
     * @param zoneId 本地日期时间所属时区
     * @return 带当前有效偏移量的日期时间
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static OffsetDateTime toOffsetDateTime(LocalDateTime dateTime, ZoneId zoneId) {
        return toZonedDateTime(dateTime, zoneId).toOffsetDateTime();
    }

    /**
     * 计算两个日期之间的完整天数。
     *
     * @param start 开始日期
     * @param end 结束日期
     * @return 从开始日期到结束日期的有符号天数
     * @throws DateOperationException 任一日期为空或计算失败时抛出
     */
    public static long betweenDays(LocalDate start, LocalDate end) {
        return executeConversion(() -> ChronoUnit.DAYS.between(
                requireArgument(start, "start"),
                requireArgument(end, "end")
        ));
    }

    /**
     * 计算两个日期时间之间的完整小时数。
     *
     * @param start 开始日期时间
     * @param end 结束日期时间
     * @return 从开始时刻到结束时刻的有符号小时数
     * @throws DateOperationException 任一参数为空或计算失败时抛出
     */
    public static long betweenHours(LocalDateTime start, LocalDateTime end) {
        return executeConversion(() -> ChronoUnit.HOURS.between(
                requireArgument(start, "start"),
                requireArgument(end, "end")
        ));
    }

    /**
     * 计算两个日期时间之间的完整分钟数。
     *
     * @param start 开始日期时间
     * @param end 结束日期时间
     * @return 从开始时刻到结束时刻的有符号分钟数
     * @throws DateOperationException 任一参数为空或计算失败时抛出
     */
    public static long betweenMinutes(LocalDateTime start, LocalDateTime end) {
        return executeConversion(() -> ChronoUnit.MINUTES.between(
                requireArgument(start, "start"),
                requireArgument(end, "end")
        ));
    }

    /**
     * 计算两个日期时间之间的完整秒数。
     *
     * @param start 开始日期时间
     * @param end 结束日期时间
     * @return 从开始时刻到结束时刻的有符号秒数
     * @throws DateOperationException 任一参数为空或计算失败时抛出
     */
    public static long betweenSeconds(LocalDateTime start, LocalDateTime end) {
        return executeConversion(() -> ChronoUnit.SECONDS.between(
                requireArgument(start, "start"),
                requireArgument(end, "end")
        ));
    }

    /**
     * 对日期时间增加指定年数。
     *
     * @param dateTime 日期时间
     * @param years 年数，允许为负数
     * @return 偏移后的日期时间
     * @throws DateOperationException 日期时间为空或计算溢出时抛出
     */
    public static LocalDateTime plusYears(LocalDateTime dateTime, long years) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime").plusYears(years));
    }

    /**
     * 对日期时间增加指定月数。
     *
     * @param dateTime 日期时间
     * @param months 月数，允许为负数
     * @return 偏移后的日期时间
     * @throws DateOperationException 日期时间为空或计算溢出时抛出
     */
    public static LocalDateTime plusMonths(LocalDateTime dateTime, long months) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime").plusMonths(months));
    }

    /**
     * 对日期时间增加指定周数。
     *
     * @param dateTime 日期时间
     * @param weeks 周数，允许为负数
     * @return 偏移后的日期时间
     * @throws DateOperationException 日期时间为空或计算溢出时抛出
     */
    public static LocalDateTime plusWeeks(LocalDateTime dateTime, long weeks) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime").plusWeeks(weeks));
    }

    /**
     * 对日期时间增加指定天数。
     *
     * @param dateTime 日期时间
     * @param days 天数，允许为负数
     * @return 偏移后的日期时间
     * @throws DateOperationException 日期时间为空或计算溢出时抛出
     */
    public static LocalDateTime plusDays(LocalDateTime dateTime, long days) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime").plusDays(days));
    }

    /**
     * 对日期时间增加指定小时数。
     *
     * @param dateTime 日期时间
     * @param hours 小时数，允许为负数
     * @return 偏移后的日期时间
     * @throws DateOperationException 日期时间为空或计算溢出时抛出
     */
    public static LocalDateTime plusHours(LocalDateTime dateTime, long hours) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime").plusHours(hours));
    }

    /**
     * 对日期时间增加指定分钟数。
     *
     * @param dateTime 日期时间
     * @param minutes 分钟数，允许为负数
     * @return 偏移后的日期时间
     * @throws DateOperationException 日期时间为空或计算溢出时抛出
     */
    public static LocalDateTime plusMinutes(LocalDateTime dateTime, long minutes) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime").plusMinutes(minutes));
    }

    /**
     * 对日期时间增加指定秒数。
     *
     * @param dateTime 日期时间
     * @param seconds 秒数，允许为负数
     * @return 偏移后的日期时间
     * @throws DateOperationException 日期时间为空或计算溢出时抛出
     */
    public static LocalDateTime plusSeconds(LocalDateTime dateTime, long seconds) {
        return executeConversion(() -> requireArgument(dateTime, "dateTime").plusSeconds(seconds));
    }

    /**
     * 获取当天开始的本地日期时间。
     *
     * @param date 日期
     * @return 当天零点
     * @throws DateOperationException 日期为空时抛出
     */
    public static LocalDateTime startOfDay(LocalDate date) {
        return executeConversion(() -> requireArgument(date, "date").atStartOfDay());
    }

    /**
     * 获取当天能够表示的最后一个本地纳秒时刻。
     *
     * <p>数据库范围查询优先使用 {@link #startOfNextDay(LocalDate)} 作为不包含的结束边界，
     * 避免不同存储精度造成遗漏。</p>
     *
     * @param date 日期
     * @return 当天 {@link LocalTime#MAX} 对应的本地日期时间
     * @throws DateOperationException 日期为空时抛出
     */
    public static LocalDateTime endOfDay(LocalDate date) {
        return executeConversion(() -> requireArgument(date, "date").atTime(LocalTime.MAX));
    }

    /**
     * 获取次日开始时刻。
     *
     * @param date 日期
     * @return 次日零点，适合作为当天范围的不包含结束边界
     * @throws DateOperationException 日期为空或计算溢出时抛出
     */
    public static LocalDateTime startOfNextDay(LocalDate date) {
        return executeConversion(() -> requireArgument(date, "date").plusDays(1).atStartOfDay());
    }

    /**
     * 构造当天的左闭右开本地日期时间范围。
     *
     * @param date 日期
     * @return 从当天开始到次日开始的不可变范围
     * @throws DateOperationException 日期为空或无法构造范围时抛出
     */
    public static DateTimeRange dayRange(LocalDate date) {
        return new DateTimeRange(startOfDay(date), startOfNextDay(date));
    }

    /**
     * 获取指定时区规则下的当天开始时刻。
     *
     * @param date 日期
     * @param zoneId 目标时区
     * @return 带完整时区规则的当天第一个有效时刻
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static ZonedDateTime startOfDay(LocalDate date, ZoneId zoneId) {
        return executeConversion(() -> requireArgument(date, "date")
                .atStartOfDay(requireArgument(zoneId, "zoneId")));
    }

    /**
     * 获取指定时区规则下的次日开始时刻。
     *
     * <p>该方法按照日期和时区规则计算，不使用固定 24 小时推算，能够正确处理夏令时短日和长日。</p>
     *
     * @param date 日期
     * @param zoneId 目标时区
     * @return 带完整时区规则的次日第一个有效时刻
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static ZonedDateTime startOfNextDay(LocalDate date, ZoneId zoneId) {
        return executeConversion(() -> requireArgument(date, "date")
                .plusDays(1)
                .atStartOfDay(requireArgument(zoneId, "zoneId")));
    }

    /**
     * 获取指定日期所在月份的第一天。
     *
     * @param date 日期
     * @return 当月第一天
     * @throws DateOperationException 日期为空时抛出
     */
    public static LocalDate firstDayOfMonth(LocalDate date) {
        return executeConversion(() -> requireArgument(date, "date")
                .with(TemporalAdjusters.firstDayOfMonth()));
    }

    /**
     * 获取指定日期所在月份的最后一天。
     *
     * @param date 日期
     * @return 当月最后一天
     * @throws DateOperationException 日期为空时抛出
     */
    public static LocalDate lastDayOfMonth(LocalDate date) {
        return executeConversion(() -> requireArgument(date, "date")
                .with(TemporalAdjusters.lastDayOfMonth()));
    }

    /**
     * 使用系统默认时区将本地日期时间转换为毫秒时间戳。
     *
     * @param dateTime 本地日期时间
     * @return Unix Epoch 毫秒数
     * @throws DateOperationException 日期时间为空或转换失败时抛出
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return toEpochMilli(dateTime, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区将本地日期时间转换为毫秒时间戳。
     *
     * @param dateTime 本地日期时间
     * @param zoneId 本地日期时间所属时区
     * @return Unix Epoch 毫秒数
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static long toEpochMilli(LocalDateTime dateTime, ZoneId zoneId) {
        return executeConversion(() -> toInstant(dateTime, zoneId).toEpochMilli());
    }

    /**
     * 使用系统默认时区将本地日期时间转换为秒时间戳。
     *
     * @param dateTime 本地日期时间
     * @return Unix Epoch 秒数
     * @throws DateOperationException 日期时间为空或转换失败时抛出
     */
    public static long toEpochSecond(LocalDateTime dateTime) {
        return toEpochSecond(dateTime, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区将本地日期时间转换为秒时间戳。
     *
     * @param dateTime 本地日期时间
     * @param zoneId 本地日期时间所属时区
     * @return Unix Epoch 秒数
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    public static long toEpochSecond(LocalDateTime dateTime, ZoneId zoneId) {
        return toInstant(dateTime, zoneId).getEpochSecond();
    }

    /**
     * 使用系统默认时区将毫秒时间戳转换为本地日期时间。
     *
     * @param epochMilli Unix Epoch 毫秒数
     * @return 系统默认时区下的本地日期时间
     * @throws DateOperationException 时间戳超出支持范围时抛出
     */
    public static LocalDateTime ofEpochMilli(long epochMilli) {
        return ofEpochMilli(epochMilli, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区将毫秒时间戳转换为本地日期时间。
     *
     * @param epochMilli Unix Epoch 毫秒数
     * @param zoneId 目标本地时区
     * @return 目标时区下的本地日期时间
     * @throws DateOperationException 时区为空或时间戳超出支持范围时抛出
     */
    public static LocalDateTime ofEpochMilli(long epochMilli, ZoneId zoneId) {
        return executeConversion(() -> LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMilli),
                requireArgument(zoneId, "zoneId")
        ));
    }

    /**
     * 使用系统默认时区将秒时间戳转换为本地日期时间。
     *
     * @param epochSecond Unix Epoch 秒数
     * @return 系统默认时区下的本地日期时间
     * @throws DateOperationException 时间戳超出支持范围时抛出
     */
    public static LocalDateTime ofEpochSecond(long epochSecond) {
        return ofEpochSecond(epochSecond, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区将秒时间戳转换为本地日期时间。
     *
     * @param epochSecond Unix Epoch 秒数
     * @param zoneId 目标本地时区
     * @return 目标时区下的本地日期时间
     * @throws DateOperationException 时区为空或时间戳超出支持范围时抛出
     */
    public static LocalDateTime ofEpochSecond(long epochSecond, ZoneId zoneId) {
        return executeConversion(() -> LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSecond),
                requireArgument(zoneId, "zoneId")
        ));
    }

    /**
     * 使用指定格式化器输出任意受支持的日期时间对象。
     *
     * @param temporal 日期时间对象
     * @param formatter 日期时间格式化器
     * @return 格式化文本
     */
    private static String formatTemporal(TemporalAccessor temporal, DateTimeFormatter formatter) {
        TemporalAccessor requiredTemporal = requireArgument(temporal, "temporal");
        DateTimeFormatter requiredFormatter = requireArgument(formatter, "formatter");
        try {
            return requiredFormatter.format(requiredTemporal);
        } catch (RuntimeException exception) {
            throw DateOperationException.formatFailed(exception);
        }
    }

    /**
     * 创建使用根区域和严格解析规则的线程安全格式化器。
     *
     * @param pattern 日期时间格式
     * @return 严格格式化器
     * @throws DateOperationException 格式为空或格式非法时抛出
     */
    public static DateTimeFormatter formatter(String pattern) {
        requireText(pattern, "pattern");
        try {
            return new DateTimeFormatterBuilder()
                    .parseCaseSensitive()
                    .appendPattern(pattern)
                    .parseDefaulting(ChronoField.ERA, IsoEra.CE.getValue())
                    .toFormatter(Locale.ROOT)
                    .withChronology(IsoChronology.INSTANCE)
                    .withResolverStyle(ResolverStyle.STRICT);
        } catch (IllegalArgumentException exception) {
            throw DateOperationException.formatFailed(exception);
        }
    }

    /**
     * 将旧版 Date 转换为指定时区下的日期时间。
     *
     * @param date 旧版 Date 对象
     * @param zoneId 目标时区
     * @return 指定时区下表示同一绝对时刻的日期时间
     * @throws DateOperationException 参数为空或转换失败时抛出
     */
    private static ZonedDateTime toZonedDateTime(Date date, ZoneId zoneId) {
        return executeConversion(() -> requireArgument(date, "date")
                .toInstant()
                .atZone(requireArgument(zoneId, "zoneId")));
    }

    /**
     * 从日期时间对象读取指定字段，并将不支持的字段转换为稳定参数异常。
     *
     * @param temporal 日期时间对象
     * @param field 需要读取的标准字段
     * @param fieldName 用于错误消息的安全字段名
     * @return 字段整数值
     */
    private static int temporalField(
            TemporalAccessor temporal,
            ChronoField field,
            String fieldName
    ) {
        TemporalAccessor requiredTemporal = requireArgument(temporal, "temporal");
        ChronoField requiredField = requireArgument(field, "field");
        if (!requiredTemporal.isSupported(requiredField)) {
            throw DateOperationException.invalidArgument(fieldName);
        }
        try {
            return requiredTemporal.get(requiredField);
        } catch (RuntimeException exception) {
            throw DateOperationException.conversionFailed(exception);
        }
    }

    /**
     * 校验自定义格式化器并强制使用严格解析规则。
     *
     * <p>调用方可以自由配置格式、区域和时区，但不能通过格式化器默认的智能解析规则
     * 绕过本工具类对非法日期的拒绝契约。</p>
     *
     * @param formatter 调用方提供的日期时间格式化器
     * @return 使用严格解析规则的不可变格式化器
     */
    private static DateTimeFormatter requireStrictFormatter(DateTimeFormatter formatter) {
        return requireArgument(formatter, "formatter").withResolverStyle(ResolverStyle.STRICT);
    }

    /**
     * 执行必选文本的严格解析，并统一转换解析异常。
     *
     * @param text 待解析文本
     * @param formatter 日期时间格式化器
     * @param parameterName 文本参数名称
     * @param parser 具体类型的解析函数
     * @param <T> 解析结果类型
     * @return 解析结果
     */
    private static <T> T parseRequired(
            String text,
            DateTimeFormatter formatter,
            String parameterName,
            BiFunction<String, DateTimeFormatter, T> parser) {
        requireText(text, parameterName);
        DateTimeFormatter requiredFormatter = requireStrictFormatter(formatter);
        try {
            return parser.apply(text, requiredFormatter);
        } catch (DateTimeParseException exception) {
            throw DateOperationException.parseFailed(exception);
        }
    }

    /**
     * 执行容错严格解析，文本为空白或无法解析时返回空结果。
     *
     * @param text 待解析文本
     * @param formatter 日期时间格式化器
     * @param parser 具体类型的解析函数
     * @param <T> 解析结果类型
     * @return 解析成功时返回结果，否则返回空结果
     */
    private static <T> Optional<T> tryParse(
            String text,
            DateTimeFormatter formatter,
            BiFunction<String, DateTimeFormatter, T> parser) {
        DateTimeFormatter requiredFormatter = requireStrictFormatter(formatter);
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.apply(text, requiredFormatter));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    /**
     * 校验严格解析所需的非空白文本。
     *
     * @param value 待校验文本
     * @param parameterName 安全参数名称
     */
    private static void requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw DateOperationException.invalidArgument(parameterName);
        }
    }

    /**
     * 校验必填参数不为空。
     *
     * @param value 待校验参数值
     * @param parameterName 安全参数名称
     * @param <T> 参数类型
     * @return 原始非空参数
     */
    private static <T> T requireArgument(T value, String parameterName) {
        if (value == null) {
            throw DateOperationException.invalidArgument(parameterName);
        }
        return value;
    }

    /**
     * 执行可能因范围、时区或旧版类型边界而失败的转换。
     *
     * @param supplier 日期时间转换逻辑
     * @param <T> 转换结果类型
     * @return 转换结果
     */
    private static <T> T executeConversion(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (DateOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw DateOperationException.conversionFailed(exception);
        }
    }
}
