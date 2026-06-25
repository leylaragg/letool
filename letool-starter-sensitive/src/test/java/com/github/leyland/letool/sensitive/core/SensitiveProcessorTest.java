package com.github.leyland.letool.sensitive.core;

import com.github.leyland.letool.sensitive.annotation.Sensitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveProcessorTest {

    static class TestUser {
        @Sensitive(type = SensitiveType.PHONE)
        private String phone = "13812345678";

        @Sensitive(type = SensitiveType.NAME)
        private String name = "张三丰";

        @Sensitive(type = SensitiveType.ID_CARD)
        private String idCard = "320123199001011234";

        @Sensitive(type = SensitiveType.EMAIL)
        private String email = "test@example.com";

        @Sensitive(type = SensitiveType.BANK_CARD)
        private String bankCard = "6222021234567890";

        @Sensitive(type = SensitiveType.CAR_LICENSE)
        private String carLicense = "京A88888";

        private String nickname = "小明";

        public String getPhone() { return phone; }
        public String getName() { return name; }
        public String getIdCard() { return idCard; }
        public String getEmail() { return email; }
        public String getBankCard() { return bankCard; }
        public String getCarLicense() { return carLicense; }
        public String getNickname() { return nickname; }
    }

    @Test
    void maskPhone_shouldReturnMasked() {
        // SensitiveProcessor 所有方法都是静态的，无需实例化
        String result = SensitiveProcessor.mask("13812345678", SensitiveType.PHONE, null);
        assertNotNull(result);
        assertFalse(result.contains("12345678"));
        assertTrue(result.startsWith("138"));
    }

    @Test
    void maskPhone_withDefaultContext_shouldReturnMasked() {
        String result = SensitiveProcessor.mask("13812345678", SensitiveType.PHONE);
        assertNotNull(result);
        assertFalse(result.contains("12345678"));
        assertTrue(result.startsWith("138"));
    }

    @Test
    void maskName_shouldReturnMasked() {
        String result = SensitiveProcessor.mask("张三丰", SensitiveType.NAME, null);
        assertNotNull(result);
        assertTrue(result.startsWith("张"));
        assertFalse(result.contains("三丰"));
    }

    @Test
    void maskIdCard_shouldReturnMasked() {
        String result = SensitiveProcessor.mask("320123199001011234", SensitiveType.ID_CARD, null);
        assertNotNull(result);
        assertTrue(result.startsWith("3201"));
        assertTrue(result.endsWith("1234"));
    }

    @Test
    void maskEmail_shouldReturnMasked() {
        String result = SensitiveProcessor.mask("test@example.com", SensitiveType.EMAIL, null);
        assertNotNull(result);
        assertTrue(result.contains("@"));
    }

    @Test
    void maskBankCard_shouldReturnMasked() {
        String result = SensitiveProcessor.mask("6222021234567890", SensitiveType.BANK_CARD, null);
        assertNotNull(result);
        assertTrue(result.startsWith("6222"));
        assertTrue(result.endsWith("7890"));
    }

    @Test
    void maskPassword_shouldFullyMask() {
        String result = SensitiveProcessor.mask("mypassword", SensitiveType.PASSWORD, null);
        assertNotNull(result);
        assertEquals("********", result);
    }

    @Test
    void maskNull_shouldReturnNull() {
        assertNull(SensitiveProcessor.mask(null, SensitiveType.PHONE, null));
    }

    @Test
    void maskEmpty_shouldReturnEmpty() {
        String result = SensitiveProcessor.mask("", SensitiveType.PHONE, null);
        assertEquals("", result);
    }

    @Test
    void maskCarLicense_shouldReturnMasked() {
        String result = SensitiveProcessor.mask("京A88888", SensitiveType.CAR_LICENSE, null);
        assertNotNull(result);
        assertTrue(result.startsWith("京A"));
        assertTrue(result.endsWith("8"));
    }

    @Test
    void maskObject_withNoSensitiveField_shouldNotMask() {
        TestUser user = new TestUser();
        TestUser result = SensitiveProcessor.mask(user);
        assertNotNull(result);
        // nickname 无 @Sensitive 注解，应保持原样
        assertEquals("小明", result.getNickname());
    }

    @Test
    void maskObject_shouldMaskAnnotatedFields() {
        TestUser user = new TestUser();
        TestUser result = SensitiveProcessor.mask(user);
        assertNotNull(result);
        // 带 @Sensitive 的字段应被脱敏
        assertFalse(result.getPhone().contains("12345678"));
        assertFalse(result.getName().contains("三丰"));
        assertFalse(result.getIdCard().contains("19900101"));
    }

    @Test
    void maskNullObject_shouldReturnNull() {
        assertNull(SensitiveProcessor.mask((TestUser) null));
    }

    @Test
    void getStrategy_shouldReturnStrategyForRegisteredType() {
        assertNotNull(SensitiveProcessor.getStrategy(SensitiveType.PHONE));
        assertNotNull(SensitiveProcessor.getStrategy(SensitiveType.NAME));
        assertNotNull(SensitiveProcessor.getStrategy(SensitiveType.EMAIL));
    }

    @Test
    void getRegisteredStrategies_shouldReturnAllRegistered() {
        var strategies = SensitiveProcessor.getRegisteredStrategies();
        assertTrue(strategies.size() >= 18);
        assertTrue(strategies.containsKey(SensitiveType.PHONE));
        assertTrue(strategies.containsKey(SensitiveType.CUSTOM));
    }
}
