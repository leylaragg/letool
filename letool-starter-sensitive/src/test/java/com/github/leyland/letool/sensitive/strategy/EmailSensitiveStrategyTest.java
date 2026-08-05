package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailSensitiveStrategy 测试")
class EmailSensitiveStrategyTest {

    private final EmailSensitiveStrategy strategy = new EmailSensitiveStrategy();

    @Nested
    @DisplayName("默认参数脱敏")
    class DefaultTests {

        @Test
        @DisplayName("标准邮箱脱敏")
        void shouldMaskStandardEmail() {
            assertEquals("t***@example.com", strategy.mask("test@example.com", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("长用户名邮箱脱敏")
        void shouldMaskLongUsername() {
            assertEquals("h***@example.com", strategy.mask("hello123@example.com", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("2字符用户名脱敏")
        void shouldMask2CharUsername() {
            assertEquals("a*@example.com", strategy.mask("ab@example.com", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("单字符用户名应完整遮盖用户名")
        void shouldNotMask1CharUsername() {
            assertEquals("*@example.com", strategy.mask("a@example.com", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("null 输入应原样返回")
        void shouldReturnNull() {
            assertNull(strategy.mask(null, MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("无 @ 符号的字符串应完整遮盖")
        void shouldReturnWithoutAt() {
            assertEquals("**********", strategy.mask("notanemail", MaskContext.DEFAULT));
        }
    }

    @Nested
    @DisplayName("自定义 Context")
    class CustomContextTests {

        @Test
        @DisplayName("自定义遮盖字符")
        void shouldCustomMaskChar() {
            MaskContext ctx = new MaskContext().withMaskChar('#');
            assertEquals("t###@example.com", strategy.mask("test@example.com", ctx));
        }

        @Test
        @DisplayName("null context 使用默认字符")
        void shouldUseDefaultsWithNullContext() {
            assertEquals("t***@example.com", strategy.mask("test@example.com", null));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("空字符串应原样返回")
        void shouldReturnEmpty() {
            assertEquals("", strategy.mask("", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("@ 在首位时应完整遮盖")
        void shouldHandleAtFirst() {
            assertEquals("************", strategy.mask("@example.com", MaskContext.DEFAULT));
        }
    }
}
