package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorUtilTest {

    @Test
    void phone() {
        assertTrue(ValidatorUtil.isPhone("13812345678"));
        assertFalse(ValidatorUtil.isPhone("12345678901"));
        assertFalse(ValidatorUtil.isPhone("1381234567"));
    }

    @Test
    void email() {
        assertTrue(ValidatorUtil.isEmail("test@example.com"));
        assertFalse(ValidatorUtil.isEmail("not-an-email"));
    }

    @Test
    void url() {
        assertTrue(ValidatorUtil.isUrl("http://example.com"));
        assertTrue(ValidatorUtil.isUrl("https://example.com/path?q=1"));
        assertFalse(ValidatorUtil.isUrl("not-a-url"));
    }

    @Test
    void idCard() {
        assertTrue(ValidatorUtil.isIdCard("320123199001011234"));
        assertFalse(ValidatorUtil.isIdCard("12345"));
    }

    @Test
    void ipV4() {
        assertTrue(ValidatorUtil.isIpV4("192.168.1.1"));
        assertTrue(ValidatorUtil.isIpV4("255.255.255.0"));
        assertFalse(ValidatorUtil.isIpV4("999.999.999.999"));
    }
}
