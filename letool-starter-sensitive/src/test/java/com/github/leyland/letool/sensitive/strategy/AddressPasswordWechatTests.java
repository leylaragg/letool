package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Address/Password/Wechat 策略测试")
class AddressPasswordWechatTests {

    @Nested
    @DisplayName("AddressSensitiveStrategy")
    class AddressTests {

        private final AddressSensitiveStrategy strategy = new AddressSensitiveStrategy();

        @Test
        @DisplayName("标准地址脱敏（默认保留一半长度）")
        void shouldMaskAddress() {
            String result = strategy.mask("北京市海淀区中关村大街1号", MaskContext.DEFAULT);
            assertNotNull(result);
            assertTrue(result.startsWith("北京市海淀区"));
            assertTrue(result.contains("*"));
        }

        @Test
        @DisplayName("短地址（<3位）不处理")
        void shouldNotMaskShortAddress() {
            assertEquals("海淀", strategy.mask("海淀", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("null 输入")
        void shouldReturnNull() {
            assertNull(strategy.mask(null, MaskContext.DEFAULT));
        }
    }

    @Nested
    @DisplayName("PasswordSensitiveStrategy")
    class PasswordTests {

        private final PasswordSensitiveStrategy strategy = new PasswordSensitiveStrategy();

        @Test
        @DisplayName("任意长度密码应全遮盖且最多8位")
        void shouldMaskPassword() {
            String result = strategy.mask("mySecret123", MaskContext.DEFAULT);
            assertEquals("********", result);
        }

        @Test
        @DisplayName("短密码应完全遮盖")
        void shouldMaskShortPassword() {
            String result = strategy.mask("abc", MaskContext.DEFAULT);
            assertEquals("***", result);
        }

        @Test
        @DisplayName("null 输入")
        void shouldReturnNull() {
            assertNull(strategy.mask(null, MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("空字符串")
        void shouldReturnEmpty() {
            assertEquals("", strategy.mask("", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("自定义遮盖字符")
        void shouldCustomMaskChar() {
            MaskContext ctx = new MaskContext().withMaskChar('#');
            String result = strategy.mask("pass", ctx);
            assertEquals("####", result);
        }

        @Test
        @DisplayName("超长密码应截断为8位")
        void shouldTruncateLongPassword() {
            String result = strategy.mask("verylongpasswordhere", MaskContext.DEFAULT);
            assertEquals("********", result);
        }
    }

    @Nested
    @DisplayName("WechatSensitiveStrategy")
    class WechatTests {

        private final WechatSensitiveStrategy strategy = new WechatSensitiveStrategy();

        @Test
        @DisplayName("标准微信号脱敏（保留首1字+末2位）")
        void shouldMaskWechat() {
            String result = strategy.mask("wechat123", MaskContext.DEFAULT);
            assertTrue(result.startsWith("w"));
            assertTrue(result.endsWith("23"));
        }

        @Test
        @DisplayName("短微信号（<3位）不处理")
        void shouldNotMaskShortWechat() {
            assertEquals("ab", strategy.mask("ab", MaskContext.DEFAULT));
        }

        @Test
        @DisplayName("null 输入")
        void shouldReturnNull() {
            assertNull(strategy.mask(null, MaskContext.DEFAULT));
        }
    }
}
