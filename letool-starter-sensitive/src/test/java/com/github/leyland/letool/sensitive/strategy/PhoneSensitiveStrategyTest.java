package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PhoneSensitiveStrategy 测试")
class PhoneSensitiveStrategyTest {

    private final PhoneSensitiveStrategy strategy = new PhoneSensitiveStrategy();

    @Nested
    @DisplayName("默认参数脱敏")
    class DefaultTests {

        @Test
        @DisplayName("标准手机号脱敏")
        void shouldMaskStandardPhone() {
            assertEquals("138****5678", strategy.mask("13812345678", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("null 输入应原样返回")
        void shouldReturnNull() {
            assertNull(strategy.mask(null, MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("短号码（<7位）应原样返回")
        void shouldReturnShortNumber() {
            assertEquals("138123", strategy.mask("138123", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("7位号码（边界）")
        void shouldMaskExact7Digits() {
            // 3+4=7, 长度刚好等于保留长度，所以原样返回
            String result = strategy.mask("1381234", MaskContext.DEFAULT);
            assertEquals("1381234", result);
        }
    }

    @Nested
    @DisplayName("自定义 Context")
    class CustomContextTests {

        @Test
        @DisplayName("自定义保留前后缀长度")
        void shouldRespectCustomKeepLength() {
            MaskContext ctx = new MaskContext().withKeepPrefix(2).withKeepSuffix(2);
            assertEquals("13*******78", strategy.mask("13812345678", ctx));
        }

        @Test
        @DisplayName("自定义遮盖字符")
        void shouldRespectCustomMaskChar() {
            MaskContext ctx = new MaskContext().withKeepPrefix(3).withKeepSuffix(4).withMaskChar('#');
            assertEquals("138####5678", strategy.mask("13812345678", ctx));
        }

        @Test
        @DisplayName("null context 使用默认值")
        void shouldUseDefaultsWithNullContext() {
            assertEquals("138****5678", strategy.mask("13812345678", null));
        }

        @Test
        @DisplayName("keepPrefix <= 0 时使用策略默认值 3")
        void shouldUseDefaultPrefixWhenZeroOrNegative() {
            MaskContext ctx = new MaskContext().withKeepSuffix(4);
            assertEquals("138****5678", strategy.mask("13812345678", ctx));
        }

        @Test
        @DisplayName("长号码也能正确脱敏")
        void shouldMaskLongPhone() {
            assertEquals("400*******8888", strategy.mask("40012345678888", MaskContext.DEFAULT));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("11位标准手机号")
        void shouldMask11DigitPhone() {
            String result = strategy.mask("13912348765", MaskContext.DEFAULT);
            assertEquals("139****8765", result);
        }

        @Test
        @DisplayName("空字符串应原样返回")
        void shouldReturnEmpty() {
            assertEquals("", strategy.mask("", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("保留长度超过原始长度时仍会遮盖（prefix+suffix < length）")
        void shouldNotReturnWhenLengthInsufficient() {
            // 5+5=10 < 11, 所以仍然会遮盖: "13812*45678"
            MaskContext ctx = new MaskContext().withKeepPrefix(5).withKeepSuffix(5);
            String result = strategy.mask("13812345678", ctx);
            assertNotNull(result);
            assertTrue(result.contains("*"));
        }
    }
}
