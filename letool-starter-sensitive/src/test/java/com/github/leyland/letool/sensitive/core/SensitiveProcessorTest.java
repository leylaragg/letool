package com.github.leyland.letool.sensitive.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SensitiveProcessor 测试")
class SensitiveProcessorTest {

    @Nested
    @DisplayName("mask 按类型默认参数")
    class MaskByTypeTests {

        @Test
        @DisplayName("PHONE 类型脱敏")
        void shouldMaskPhone() {
            assertEquals("138****5678", SensitiveProcessor.mask("13812345678", SensitiveType.PHONE));
        }

        @Test
        @DisplayName("ID_CARD 类型脱敏")
        void shouldMaskIdCard() {
            assertEquals("3201**********1234", SensitiveProcessor.mask("320123199001011234", SensitiveType.ID_CARD));
        }

        @Test
        @DisplayName("EMAIL 类型脱敏")
        void shouldMaskEmail() {
            assertEquals("t***@example.com", SensitiveProcessor.mask("test@example.com", SensitiveType.EMAIL));
        }

        @Test
        @DisplayName("NAME 类型脱敏")
        void shouldMaskName() {
            assertEquals("张*", SensitiveProcessor.mask("张三", SensitiveType.NAME));
        }

        @Test
        @DisplayName("BANK_CARD 类型脱敏")
        void shouldMaskBankCard() {
            assertEquals("6222********7890", SensitiveProcessor.mask("6222021234567890", SensitiveType.BANK_CARD));
        }

        @Test
        @DisplayName("PASSWORD 类型脱敏")
        void shouldMaskPassword() {
            assertEquals("********", SensitiveProcessor.mask("mySecret123", SensitiveType.PASSWORD));
        }

        @Test
        @DisplayName("null 值应原样返回")
        void shouldReturnNull() {
            assertNull(SensitiveProcessor.mask(null, SensitiveType.PHONE));
        }

        @Test
        @DisplayName("空字符串应原样返回")
        void shouldReturnEmpty() {
            assertEquals("", SensitiveProcessor.mask("", SensitiveType.PHONE));
        }
    }

    @Nested
    @DisplayName("mask 按类型+自定义Context")
    class MaskWithContextTests {

        @Test
        @DisplayName("自定义 Context 脱敏")
        void shouldMaskWithCustomContext() {
            MaskContext ctx = new MaskContext().withKeepPrefix(2).withKeepSuffix(2).withMaskChar('#');
            assertEquals("13#######78", SensitiveProcessor.mask("13812345678", SensitiveType.PHONE, ctx));
        }
    }

    @Nested
    @DisplayName("mask 对象脱敏")
    class MaskObjectTests {

        @Test
        @DisplayName("应克隆对象并脱敏 @Sensitive 字段")
        void shouldMaskObjectFields() {
            TestUser user = new TestUser();
            user.setName("张三");
            user.setPhone("13812345678");
            user.setIdCard("320123199001011234");

            TestUser masked = SensitiveProcessor.mask(user);

            assertNotNull(masked);
            assertNotSame(user, masked);
            assertEquals("张*", masked.getName());
            assertEquals("138****5678", masked.getPhone());
            assertEquals("3201**********1234", masked.getIdCard());
        }

        @Test
        @DisplayName("null 对象应返回 null")
        void shouldReturnNullForNullObject() {
            assertNull(SensitiveProcessor.mask(null));
        }
    }

    @Nested
    @DisplayName("策略注册表")
    class StrategyRegistryTests {

        @Test
        @DisplayName("getRegisteredStrategies 应返回所有已注册策略")
        void shouldReturnAllStrategies() {
            Map<SensitiveType, SensitiveStrategy<MaskContext>> strategies = SensitiveProcessor.getRegisteredStrategies();
            assertFalse(strategies.isEmpty());
            assertTrue(strategies.containsKey(SensitiveType.PHONE));
            assertTrue(strategies.containsKey(SensitiveType.ID_CARD));
            assertTrue(strategies.containsKey(SensitiveType.EMAIL));
            assertTrue(strategies.containsKey(SensitiveType.NAME));
        }

        @Test
        @DisplayName("getStrategy 应返回指定类型策略")
        void shouldReturnSpecificStrategy() {
            SensitiveStrategy<MaskContext> strategy = SensitiveProcessor.getStrategy(SensitiveType.PHONE);
            assertNotNull(strategy);
        }
    }

    // ======================== 测试用内部类 ========================

    public static class TestUser {
        @com.github.leyland.letool.sensitive.annotation.Sensitive(type = SensitiveType.NAME)
        private String name;

        @com.github.leyland.letool.sensitive.annotation.Sensitive(type = SensitiveType.PHONE)
        private String phone;

        @com.github.leyland.letool.sensitive.annotation.Sensitive(type = SensitiveType.ID_CARD)
        private String idCard;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
    }
}
