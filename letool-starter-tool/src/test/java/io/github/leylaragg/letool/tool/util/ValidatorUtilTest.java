package io.github.leylaragg.letool.tool.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 常用格式校验器的高风险边界测试。
 */
class ValidatorUtilTest {

    /**
     * 验证中国大陆身份证日期和 GB 11643 校验位。
     */
    @Test
    void shouldValidateIdDateAndChecksum() {
        assertTrue(ValidatorUtil.isIdCard("11010519491231002X"));
        assertFalse(ValidatorUtil.isIdCard("110105194912310021"));
        assertFalse(ValidatorUtil.isIdCard("11010519990230002X"));
    }

    /**
     * 验证 URL 必须具有 HTTP 协议、合法主机和有效端口。
     */
    @Test
    void shouldValidateHttpUrlSemantics() {
        assertTrue(ValidatorUtil.isUrl("https://example.com:8443/path?q=1"));
        assertFalse(ValidatorUtil.isUrl("ftp://example.com/file"));
        assertFalse(ValidatorUtil.isUrl("http:///missing-host"));
        assertFalse(ValidatorUtil.isUrl("https://example.com:70000/path"));
    }

    /**
     * 验证邮箱长度和本地部分的连续点等常见错误。
     */
    @Test
    void shouldRejectUnsafeEmailEdgeCases() {
        assertTrue(ValidatorUtil.isEmail("user.name+tag@example.com"));
        assertFalse(ValidatorUtil.isEmail("user..name@example.com"));
        assertFalse(ValidatorUtil.isEmail(".user@example.com"));
        assertFalse(ValidatorUtil.isEmail("a".repeat(65) + "@example.com"));
    }

    /**
     * 验证 IPv4 采用规范十进制分段，不接受前导零和越界值。
     */
    @Test
    void shouldValidateCanonicalIpv4Segments() {
        assertTrue(ValidatorUtil.isIpV4("192.168.1.1"));
        assertTrue(ValidatorUtil.isIpV4("0.0.0.0"));
        assertFalse(ValidatorUtil.isIpV4("192.168.001.1"));
        assertFalse(ValidatorUtil.isIpV4("256.1.1.1"));
    }

    /**
     * 验证动态正则配置错误时安全返回不匹配，而不是中断业务流程。
     */
    @Test
    void shouldTreatInvalidDynamicRegexAsNoMatch() {
        assertFalse(ValidatorUtil.matches("value", "["));
    }
}
