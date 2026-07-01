package com.github.leyland.letool.excel.convert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EnumConverter 枚举转换器测试")
class EnumConverterTest {

    private EnumConverter<TestStatus> converter;

    enum TestStatus {
        ACTIVE, INACTIVE, PENDING
    }

    @BeforeEach
    void setUp() {
        converter = new EnumConverter<>(TestStatus.class);
    }

    @Nested
    @DisplayName("convertToJava 测试")
    class ConvertToJavaTests {

        @Test
        @DisplayName("应精确匹配枚举名称（大写）")
        void shouldMatchExactName() {
            assertEquals(TestStatus.ACTIVE, converter.convertToJava("ACTIVE"));
            assertEquals(TestStatus.PENDING, converter.convertToJava("PENDING"));
        }

        @Test
        @DisplayName("应忽略大小写匹配")
        void shouldMatchCaseInsensitive() {
            assertEquals(TestStatus.ACTIVE, converter.convertToJava("active"));
            assertEquals(TestStatus.INACTIVE, converter.convertToJava("InActive"));
            assertEquals(TestStatus.PENDING, converter.convertToJava("pending"));
        }

        @Test
        @DisplayName("应去除首尾空白")
        void shouldTrimWhitespace() {
            assertEquals(TestStatus.ACTIVE, converter.convertToJava("  ACTIVE  "));
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
        @DisplayName("未知枚举值应抛出异常")
        void unknownValueShouldThrow() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> converter.convertToJava("UNKNOWN"));
            assertTrue(ex.getMessage().contains("UNKNOWN"));
        }
    }

    @Nested
    @DisplayName("convertToExcel 测试")
    class ConvertToExcelTests {

        @Test
        @DisplayName("应返回枚举的大写名称")
        void shouldReturnEnumName() {
            assertEquals("ACTIVE", converter.convertToExcel(TestStatus.ACTIVE));
            assertEquals("PENDING", converter.convertToExcel(TestStatus.PENDING));
        }

        @Test
        @DisplayName("null 应返回空字符串")
        void nullShouldReturnEmptyString() {
            assertEquals("", converter.convertToExcel(null));
        }
    }

    @Nested
    @DisplayName("双向转换一致性测试")
    class RoundTripTests {

        @Test
        @DisplayName("convertToExcel → convertToJava 应一致")
        void roundTripShouldBeConsistent() {
            TestStatus original = TestStatus.INACTIVE;
            String excel = converter.convertToExcel(original);
            TestStatus parsed = converter.convertToJava(excel);
            assertEquals(original, parsed);
        }
    }
}
