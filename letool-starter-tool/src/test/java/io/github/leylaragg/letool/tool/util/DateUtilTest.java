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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(LocalDate.of(2024, 1, 1), DateUtil.firstDayOfMonth(endDate));
        assertEquals(LocalDate.of(2024, 1, 31), DateUtil.lastDayOfMonth(endDate));
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
