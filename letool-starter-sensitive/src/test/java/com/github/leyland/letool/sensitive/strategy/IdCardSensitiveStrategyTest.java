package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IdCardSensitiveStrategy 测试")
class IdCardSensitiveStrategyTest {

    private final IdCardSensitiveStrategy strategy = new IdCardSensitiveStrategy();

    @Nested
    @DisplayName("默认参数脱敏")
    class DefaultTests {

        @Test
        @DisplayName("18位身份证脱敏")
        void shouldMask18DigitIdCard() {
            assertEquals("3201**********1234", strategy.mask("320123199001011234", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("15位旧身份证脱敏")
        void shouldMask15DigitIdCard() {
            assertEquals("3201*******8901", strategy.mask("320123456788901", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("null 输入应原样返回")
        void shouldReturnNull() {
            assertNull(strategy.mask(null, MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("短字符串（<8位）应原样返回")
        void shouldReturnShort() {
            assertEquals("32012", strategy.mask("32012", MaskContext.DEFAULT));
        }
    }

    @Nested
    @DisplayName("自定义 Context")
    class CustomContextTests {

        @Test
        @DisplayName("自定义遮盖字符")
        void shouldCustomMaskChar() {
            MaskContext ctx = new MaskContext().withKeepPrefix(4).withKeepSuffix(4).withMaskChar('#');
            assertEquals("3201##########1234", strategy.mask("320123199001011234", ctx));
        }

        @Test
        @DisplayName("null context 使用默认值")
        void shouldUseDefaultsWithNullContext() {
            assertEquals("3201**********1234", strategy.mask("320123199001011234", null));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("恰好 8 位：长度不足以遮盖，原样返回")
        void shouldMaskExactly8Digits() {
            // 4+4=8, length=8, 长度等于保留长度, 原样返回
            assertEquals("32011234", strategy.mask("32011234", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("空字符串应原样返回")
        void shouldReturnEmpty() {
            assertEquals("", strategy.mask("", MaskContext.DEFAULT));
        }
    }
}
