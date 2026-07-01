package com.github.leyland.letool.excel.convert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DateConverter 日期转换器测试")
class DateConverterTest {

    private DateConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DateConverter("yyyy-MM-dd");
    }

    @Nested
    @DisplayName("convertToJava 测试")
    class ConvertToJavaTests {

        @Test
        @DisplayName("应正确解析日期字符串")
        void shouldParseDateString() {
            LocalDate result = converter.convertToJava("2024-06-15");
            assertEquals(LocalDate.of(2024, 6, 15), result);
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void nullInputShouldReturnNull() {
            assertNull(converter.convertToJava(null));
        }

        @Test
        @DisplayName("空字符串应返回 null")
        void emptyStringShouldReturnNull() {
            assertNull(converter.convertToJava(""));
        }

        @Test
        @DisplayName("格式不匹配应抛出异常")
        void invalidFormatShouldThrow() {
            assertThrows(DateTimeParseException.class, () ->
                    converter.convertToJava("2024/06/15"));
        }

        @Test
        @DisplayName("非日期字符串应抛出异常")
        void nonDateStringShouldThrow() {
            assertThrows(DateTimeParseException.class, () ->
                    converter.convertToJava("not-a-date"));
        }
    }

    @Nested
    @DisplayName("convertToExcel 测试")
    class ConvertToExcelTests {

        @Test
        @DisplayName("应正确格式化日期")
        void shouldFormatDate() {
            LocalDate date = LocalDate.of(2024, 12, 25);
            assertEquals("2024-12-25", converter.convertToExcel(date));
        }

        @Test
        @DisplayName("null 应返回空字符串")
        void nullShouldReturnEmptyString() {
            assertEquals("", converter.convertToExcel(null));
        }
    }

    @Nested
    @DisplayName("不同格式模式测试")
    class DifferentPatternTests {

        @Test
        @DisplayName("应支持自定义日期格式")
        void shouldSupportCustomPattern() {
            DateConverter customConverter = new DateConverter("dd/MM/yyyy");
            LocalDate result = customConverter.convertToJava("31/12/2024");
            assertEquals(LocalDate.of(2024, 12, 31), result);
        }

        @Test
        @DisplayName("自定义格式的 convertToExcel 应正确格式化")
        void customPatternExportShouldFormatCorrectly() {
            DateConverter customConverter = new DateConverter("MM-dd-yyyy");
            assertEquals("12-25-2024", customConverter.convertToExcel(LocalDate.of(2024, 12, 25)));
        }
    }

    @Nested
    @DisplayName("双向转换一致性测试")
    class RoundTripTests {

        @Test
        @DisplayName("convertToJava → convertToExcel 应一致")
        void roundTripShouldBeConsistent() {
            LocalDate original = LocalDate.of(2024, 8, 1);
            String excel = converter.convertToExcel(original);
            LocalDate parsed = converter.convertToJava(excel);
            assertEquals(original, parsed);
        }
    }
}
