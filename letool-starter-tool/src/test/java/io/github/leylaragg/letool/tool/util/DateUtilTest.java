package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.date.DateErrorCode;
import io.github.leylaragg.letool.tool.date.DateOperationException;
import io.github.leylaragg.letool.tool.date.DateTimeRange;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DateUtil} 的严格解析、时区转换和时间范围契约测试。
 */
class DateUtilTest {

    /**
     * 验证标准格式化与严格解析保持常用输出，并通过稳定错误码隐藏非法原文。
     */
    @Test
    void shouldFormatAndStrictlyParseStandardValues() {
        LocalDate date = LocalDate.of(2024, 2, 29);
        LocalDateTime dateTime = LocalDateTime.of(2024, 2, 29, 10, 30);

        assertEquals("2024-02-29", DateUtil.formatDate(date));
        assertEquals("2024-02-29 10:30:00", DateUtil.formatDateTime(dateTime));
        assertEquals("10:30:00", DateUtil.formatTime(LocalTime.of(10, 30)));
        assertEquals(date, DateUtil.parseDate("2024-02-29"));
        assertEquals(dateTime, DateUtil.parseDateTime("2024-02-29 10:30:00"));

        DateOperationException exception = assertThrows(
                DateOperationException.class,
                () -> DateUtil.parseDate("2024-02-30-secret")
        );
        assertEquals(DateErrorCode.PARSE_FAILED.getCode(), exception.getCode());
        assertFalse(exception.getMessage().contains("2024-02-30-secret"));
        assertEquals(Optional.empty(), DateUtil.tryParseDate("2024-02-30"));
        assertEquals(Optional.empty(), DateUtil.tryParseDateTime(" "));
        assertThrows(
                DateOperationException.class,
                () -> DateUtil.parseDate("2024-02-30", DateTimeFormatter.ofPattern("uuuu-MM-dd"))
        );
    }

    /**
     * 验证自定义格式既能覆盖常见写法，又不会放宽非法日期解析规则。
     */
    @Test
    void shouldFormatAndParseCustomPatternsStrictly() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 2, 29, 10, 30, 15, 123_000_000);

        assertEquals("2024-02", DateUtil.format(dateTime, DateUtil.STD_MONTH));
        assertEquals("2024-02-29 10:30:15.123", DateUtil.format(dateTime, DateUtil.STD_DATETIME_MILLI));
        assertEquals("103015", DateUtil.format(dateTime, DateUtil.PURE_TIME));
        assertEquals("20240229103015123", DateUtil.format(dateTime, DateUtil.PURE_DATETIME_MILLI));
        assertEquals("2024/02/29", DateUtil.format(dateTime, "yyyy/MM/dd"));
        assertEquals(LocalDate.of(2024, 2, 29), DateUtil.parseDate("2024/02/29", "yyyy/MM/dd"));
        assertEquals(
                dateTime.withNano(0),
                DateUtil.parseDateTime("2024/02/29 10:30:15", "uuuu/MM/dd HH:mm:ss")
        );
        assertEquals(LocalTime.of(10, 30, 15), DateUtil.parseTime("10:30:15"));
        assertEquals(LocalTime.of(10, 30), DateUtil.parseTime("10点30分", "HH点mm分"));
        assertEquals(Optional.empty(), DateUtil.tryParseTime("25:00:00"));

        DateOperationException invalidDate = assertThrows(
                DateOperationException.class,
                () -> DateUtil.parseDate("2024/02/30", "yyyy/MM/dd")
        );
        assertEquals(DateErrorCode.PARSE_FAILED.getCode(), invalidDate.getCode());

        DateOperationException invalidPattern = assertThrows(
                DateOperationException.class,
                () -> DateUtil.formatter("yyyy-MM-dd '")
        );
        assertEquals(DateErrorCode.FORMAT_FAILED.getCode(), invalidPattern.getCode());
        assertThrows(
                DateOperationException.class,
                () -> DateUtil.tryParseDate(" ", (DateTimeFormatter) null)
        );
    }

    /**
     * 验证旧版 Date 的格式化始终按照调用方指定的时区解释绝对时刻。
     */
    @Test
    void shouldFormatLegacyDateInExplicitZone() {
        Date date = Date.from(Instant.parse("2024-01-01T16:30:15Z"));
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");

        assertEquals(
                "2024-01-02 00:30:15",
                DateUtil.format(date, DateUtil.STD_DATETIME, shanghai)
        );
        assertEquals(
                "2024/01/02 +08:00",
                DateUtil.format(date, "yyyy/MM/dd XXX", shanghai)
        );
        DateTimeFormatter utcFormatter = DateUtil.formatter("yyyy/MM/dd HH:mm XXX")
                .withZone(ZoneOffset.UTC);
        assertEquals(
                "2024/01/02 00:30 +08:00",
                DateUtil.format(date, utcFormatter, shanghai)
        );
    }

    /**
     * 验证公共格式器可以由并发业务安全复用，不依赖每次调用重新创建实例。
     */
    @Test
    void shouldReuseFormatterSafelyAcrossThreads() {
        DateTimeFormatter formatter = DateUtil.formatter("yyyy-MM-dd HH:mm:ss");
        LocalDateTime value = LocalDateTime.of(2024, 2, 29, 10, 30, 15);

        assertTrue(IntStream.range(0, 200)
                .parallel()
                .mapToObj(index -> DateUtil.format(value, formatter))
                .allMatch("2024-02-29 10:30:15"::equals));
    }

    /**
     * 验证固定时钟和显式时区可以稳定控制当前时间及所有转换结果。
     */
    @Test
    void shouldUseExplicitClockAndZoneForDeterministicTimeConversion() {
        Instant instant = Instant.parse("2024-01-01T16:30:00Z");
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Clock utcClock = Clock.fixed(instant, ZoneOffset.UTC);
        Clock shanghaiClock = Clock.fixed(instant, shanghai);

        assertEquals(LocalDate.of(2024, 1, 1), DateUtil.today(utcClock));
        assertEquals(LocalDate.of(2024, 1, 2), DateUtil.today(shanghaiClock));
        assertEquals(LocalDateTime.of(2024, 1, 2, 0, 30), DateUtil.now(shanghaiClock));

        LocalDateTime localDateTime = LocalDateTime.of(2024, 1, 2, 0, 30);
        Date legacyDate = DateUtil.toDate(localDateTime, shanghai);
        ZonedDateTime zonedDateTime = DateUtil.toZonedDateTime(localDateTime, shanghai);
        OffsetDateTime offsetDateTime = DateUtil.toOffsetDateTime(localDateTime, shanghai);

        assertEquals(instant, DateUtil.toInstant(localDateTime, shanghai));
        assertEquals(localDateTime, DateUtil.toLocalDateTime(instant, shanghai));
        assertEquals(localDateTime, DateUtil.toLocalDateTime(legacyDate, shanghai));
        assertEquals(shanghai, zonedDateTime.getZone());
        assertEquals(ZoneOffset.ofHours(8), offsetDateTime.getOffset());
        assertEquals(instant.toEpochMilli(), DateUtil.toEpochMilli(localDateTime, shanghai));
        assertEquals(instant.getEpochSecond(), DateUtil.toEpochSecond(localDateTime, shanghai));
        assertEquals(localDateTime, DateUtil.ofEpochMilli(instant.toEpochMilli(), shanghai));
        assertEquals(localDateTime, DateUtil.ofEpochSecond(instant.getEpochSecond(), shanghai));
    }

    /**
     * 验证当前字段与文本入口完整遵循 Clock 携带的时刻和时区。
     */
    @Test
    void shouldReadCurrentFieldsFromClock() {
        Clock clock = Clock.fixed(
                Instant.parse("2024-12-31T16:05:06Z"),
                ZoneId.of("Asia/Shanghai")
        );

        assertEquals(2025, DateUtil.currentYear(clock));
        assertEquals(1, DateUtil.currentMonth(clock));
        assertEquals(Month.JANUARY, DateUtil.currentMonthEnum(clock));
        assertEquals(1, DateUtil.currentDay(clock));
        assertEquals(0, DateUtil.currentHour(clock));
        assertEquals(5, DateUtil.currentMinute(clock));
        assertEquals(6, DateUtil.currentSecond(clock));
        assertEquals("2025-01-01", DateUtil.todayText(clock));
        assertEquals("2025-01-01 00:05:06", DateUtil.nowText(clock));

        assertTrue(DateUtil.currentYear() > 0);
        assertTrue(DateUtil.currentMonth() >= 1 && DateUtil.currentMonth() <= 12);
        assertNotNull(DateUtil.currentMonthEnum());
        assertTrue(DateUtil.currentDay() >= 1 && DateUtil.currentDay() <= 31);
        assertTrue(DateUtil.currentHour() >= 0 && DateUtil.currentHour() <= 23);
        assertTrue(DateUtil.currentMinute() >= 0 && DateUtil.currentMinute() <= 59);
        assertTrue(DateUtil.currentSecond() >= 0 && DateUtil.currentSecond() <= 59);
        assertNotNull(DateUtil.todayText());
        assertNotNull(DateUtil.nowText());
    }

    /**
     * 验证字段读取同时覆盖 java.time 与旧版 Date，并拒绝对象不支持的字段。
     */
    @Test
    void shouldReadFieldsFromTemporalAndLegacyDate() {
        ZonedDateTime temporal = ZonedDateTime.of(
                2024,
                2,
                29,
                23,
                58,
                57,
                0,
                ZoneId.of("Asia/Shanghai")
        );
        Date legacyDate = Date.from(Instant.parse("2024-02-29T16:30:15Z"));

        assertEquals(2024, DateUtil.year(temporal));
        assertEquals(2, DateUtil.month(temporal));
        assertEquals(Month.FEBRUARY, DateUtil.monthEnum(temporal));
        assertEquals(29, DateUtil.day(temporal));
        assertEquals(23, DateUtil.hour(temporal));
        assertEquals(58, DateUtil.minute(temporal));
        assertEquals(57, DateUtil.second(temporal));

        assertEquals(29, DateUtil.day(legacyDate, ZoneOffset.UTC));
        assertEquals(2024, DateUtil.year(legacyDate, ZoneOffset.UTC));
        assertEquals(2, DateUtil.month(legacyDate, ZoneOffset.UTC));
        assertEquals(Month.FEBRUARY, DateUtil.monthEnum(legacyDate, ZoneOffset.UTC));
        assertEquals(16, DateUtil.hour(legacyDate, ZoneOffset.UTC));
        assertEquals(30, DateUtil.minute(legacyDate, ZoneOffset.UTC));
        assertEquals(15, DateUtil.second(legacyDate, ZoneOffset.UTC));
        assertEquals(1, DateUtil.day(legacyDate, ZoneId.of("Asia/Shanghai")));
        assertEquals(0, DateUtil.hour(legacyDate, ZoneId.of("Asia/Shanghai")));

        LocalDateTime systemLocalDateTime = DateUtil.toLocalDateTime(legacyDate);
        assertEquals(systemLocalDateTime.getYear(), DateUtil.year(legacyDate));
        assertEquals(systemLocalDateTime.getMonthValue(), DateUtil.month(legacyDate));
        assertEquals(systemLocalDateTime.getMonth(), DateUtil.monthEnum(legacyDate));
        assertEquals(systemLocalDateTime.getDayOfMonth(), DateUtil.day(legacyDate));
        assertEquals(systemLocalDateTime.getHour(), DateUtil.hour(legacyDate));
        assertEquals(systemLocalDateTime.getMinute(), DateUtil.minute(legacyDate));
        assertEquals(systemLocalDateTime.getSecond(), DateUtil.second(legacyDate));

        DateOperationException unsupported = assertThrows(
                DateOperationException.class,
                () -> DateUtil.hour(LocalDate.of(2024, 1, 1))
        );
        assertEquals(DateErrorCode.INVALID_ARGUMENT.getCode(), unsupported.getCode());
    }

    /**
     * 验证必填参数不再静默返回空值或零，并统一包装格式化失败。
     */
    @Test
    void shouldRejectNullArgumentsAndWrapFormattingFailure() {
        DateOperationException nullException = assertThrows(
                DateOperationException.class,
                () -> DateUtil.betweenDays(null, LocalDate.of(2024, 1, 1))
        );
        DateOperationException formatException = assertThrows(
                DateOperationException.class,
                () -> DateUtil.format(
                        LocalDate.of(2024, 1, 1),
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                )
        );

        assertEquals(DateErrorCode.INVALID_ARGUMENT.getCode(), nullException.getCode());
        assertEquals(DateErrorCode.FORMAT_FAILED.getCode(), formatException.getCode());
        assertThrows(DateOperationException.class, () -> DateUtil.formatDate(null));
        assertThrows(DateOperationException.class, () -> DateUtil.parseDate(" "));
    }

    /**
     * 验证常用差值、偏移和月边界组合可直接服务业务计算。
     */
    @Test
    void shouldCalculateCommonDateTimeArithmetic() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 10);
        LocalDateTime startTime = startDate.atStartOfDay();

        assertEquals(9, DateUtil.betweenDays(startDate, endDate));
        assertEquals(26, DateUtil.betweenHours(startTime, startTime.plusHours(26)));
        assertEquals(90, DateUtil.betweenMinutes(startTime, startTime.plusMinutes(90)));
        assertEquals(startTime.plusDays(2), DateUtil.plusDays(startTime, 2));
        assertEquals(startTime.plusHours(3), DateUtil.plusHours(startTime, 3));
        assertEquals(startTime.minusMinutes(5), DateUtil.plusMinutes(startTime, -5));
        assertEquals(startTime.plusYears(1), DateUtil.plusYears(startTime, 1));
        assertEquals(startTime.plusMonths(2), DateUtil.plusMonths(startTime, 2));
        assertEquals(startTime.minusWeeks(1), DateUtil.plusWeeks(startTime, -1));
        assertEquals(startTime.plusSeconds(75), DateUtil.plusSeconds(startTime, 75));
        assertEquals(
                LocalDateTime.of(2025, 2, 28, 12, 0),
                DateUtil.plusYears(LocalDateTime.of(2024, 2, 29, 12, 0), 1)
        );
        assertEquals(
                LocalDateTime.of(2024, 2, 29, 12, 0),
                DateUtil.plusMonths(LocalDateTime.of(2024, 1, 31, 12, 0), 1)
        );
        assertEquals(75, DateUtil.betweenSeconds(startTime, startTime.plusSeconds(75)));
        assertEquals(-75, DateUtil.betweenSeconds(startTime.plusSeconds(75), startTime));
        assertEquals(0, DateUtil.betweenSeconds(startTime, startTime.plusNanos(999_999_999)));
        assertEquals(LocalDate.of(2024, 1, 1), DateUtil.firstDayOfMonth(endDate));
        assertEquals(LocalDate.of(2024, 1, 31), DateUtil.lastDayOfMonth(endDate));

        DateOperationException overflow = assertThrows(
                DateOperationException.class,
                () -> DateUtil.plusSeconds(LocalDateTime.MAX, 1)
        );
        assertEquals(DateErrorCode.CONVERSION_FAILED.getCode(), overflow.getCode());
    }

    /**
     * 验证日区间采用左闭右开语义，带时区边界能够正确处理夏令时短日。
     */
    @Test
    void shouldBuildHalfOpenDayRangeAndRespectDaylightSavingTime() {
        LocalDate date = LocalDate.of(2024, 3, 10);
        DateTimeRange range = DateUtil.dayRange(date);

        assertEquals(date.atStartOfDay(), DateUtil.startOfDay(date));
        assertEquals(date.atTime(LocalTime.MAX), DateUtil.endOfDay(date));
        assertEquals(date.plusDays(1).atStartOfDay(), DateUtil.startOfNextDay(date));
        assertEquals(date.atStartOfDay(), range.startInclusive());
        assertEquals(date.plusDays(1).atStartOfDay(), range.endExclusive());
        assertTrue(range.contains(range.startInclusive()));
        assertFalse(range.contains(range.endExclusive()));
        assertEquals(Duration.ofDays(1), range.duration());

        ZoneId newYork = ZoneId.of("America/New_York");
        ZonedDateTime zonedStart = DateUtil.startOfDay(date, newYork);
        ZonedDateTime zonedEnd = DateUtil.startOfNextDay(date, newYork);
        assertEquals(Duration.ofHours(23), Duration.between(zonedStart, zonedEnd));

        DateOperationException exception = assertThrows(
                DateOperationException.class,
                () -> new DateTimeRange(range.endExclusive(), range.startInclusive())
        );
        assertEquals(DateErrorCode.INVALID_ARGUMENT.getCode(), exception.getCode());
    }
}
