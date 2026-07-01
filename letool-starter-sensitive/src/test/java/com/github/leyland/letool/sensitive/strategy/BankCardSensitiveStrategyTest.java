package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BankCardSensitiveStrategy 测试")
class BankCardSensitiveStrategyTest {

    private final BankCardSensitiveStrategy strategy = new BankCardSensitiveStrategy();

    @Nested
    @DisplayName("默认参数脱敏")
    class DefaultTests {

        @Test
        @DisplayName("16位银行卡脱敏")
        void shouldMask16DigitBankCard() {
            assertEquals("6222********7890", strategy.mask("6222021234567890", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("19位银行卡脱敏")
        void shouldMask19DigitBankCard() {
            assertEquals("6222***********0123", strategy.mask("6222021234567890123", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("null 输入应原样返回")
        void shouldReturnNull() {
            assertNull(strategy.mask(null, MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("短卡号（<8位）应原样返回")
        void shouldReturnShort() {
            assertEquals("622202", strategy.mask("622202", MaskContext.DEFAULT));
        }
    }

    @Nested
    @DisplayName("自定义 Context")
    class CustomContextTests {

        @Test
        @DisplayName("自定义遮盖字符")
        void shouldCustomMaskChar() {
            MaskContext ctx = new MaskContext().withKeepPrefix(4).withKeepSuffix(4).withMaskChar('#');
            assertEquals("6222########7890", strategy.mask("6222021234567890", ctx));
        }

        @Test
        @DisplayName("null context 使用默认值")
        void shouldUseDefaultsWithNullContext() {
            assertEquals("6222********7890", strategy.mask("6222021234567890", null));
        }

        @Test
        @DisplayName("自定义保留前后缀")
        void shouldCustomKeepLength() {
            MaskContext ctx = new MaskContext().withKeepPrefix(6).withKeepSuffix(4);
            assertEquals("622202******7890", strategy.mask("6222021234567890", ctx));
        }
    }
}
