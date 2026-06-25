package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilTest {

    @Test
    void formatAndParse() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        assertEquals("2024-01-15", DateUtil.formatDate(date));

        LocalDateTime dt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        assertEquals("2024-01-15 10:30:00", DateUtil.formatDateTime(dt));
    }

    @Test
    void parseDate() {
        LocalDate date = DateUtil.parseDate("2024-01-15");
        assertEquals(LocalDate.of(2024, 1, 15), date);
    }

    @Test
    void parseDateTime() {
        LocalDateTime dt = DateUtil.parseDateTime("2024-01-15 10:30:00");
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0), dt);
    }

    @Test
    void dateConversion() {
        LocalDateTime dt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        Date date = DateUtil.toDate(dt);
        LocalDateTime back = DateUtil.toLocalDateTime(date);
        assertEquals(dt, back);
    }

    @Test
    void betweenDays() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 10);
        assertEquals(9, DateUtil.betweenDays(start, end));
    }

    @Test
    void startEndOfDay() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        assertEquals(LocalDateTime.of(2024, 1, 15, 0, 0, 0), DateUtil.startOfDay(date));
        assertEquals(LocalDateTime.of(2024, 1, 15, 23, 59, 59), DateUtil.endOfDay(date));
    }

    @Test
    void firstLastDayOfMonth() {
        LocalDate date = LocalDate.of(2024, 6, 15);
        assertEquals(LocalDate.of(2024, 6, 1), DateUtil.firstDayOfMonth(date));
        assertEquals(LocalDate.of(2024, 6, 30), DateUtil.lastDayOfMonth(date));
    }

    @Test
    void epochConversion() {
        LocalDateTime dt = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        long milli = DateUtil.toEpochMilli(dt);
        LocalDateTime back = DateUtil.ofEpochMilli(milli);
        assertEquals(dt, back);
    }
}
