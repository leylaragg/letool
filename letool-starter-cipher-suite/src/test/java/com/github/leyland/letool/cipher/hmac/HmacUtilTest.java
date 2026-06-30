package com.github.leyland.letool.cipher.hmac;

import com.github.leyland.letool.cipher.key.KeyGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HMAC 消息认证码单元测试.
 */
@DisplayName("HMAC 消息认证码测试")
class HmacUtilTest {

    private static String key;
    private static String otherKey;

    @BeforeAll
    static void setUpKeys() {
        key = "my-secret-hmac-key-32bytes!!";
        otherKey = "other-secret-hmac-key-32bytes!";
    }

    // ===================== HMAC-SHA256（十六进制）测试 =====================

    @Nested
    @DisplayName("HMAC-SHA256（十六进制）测试")
    class HmacSha256HexTests {

        @Test
        @DisplayName("HMAC-SHA256 应返回 64 位十六进制字符串")
        void shouldReturn64HexChars() {
            String result = HmacUtil.hmacSha256("hello", key);
            assertNotNull(result);
            assertEquals(64, result.length(), "HMAC-SHA256 应返回 64 位十六进制字符串");
        }

        @Test
        @DisplayName("同一数据和密钥应产生相同 HMAC-SHA256")
        void shouldBeDeterministic() {
            String data = "deterministic hmac test";
            String hmac1 = HmacUtil.hmacSha256(data, key);
            String hmac2 = HmacUtil.hmacSha256(data, key);
            assertEquals(hmac1, hmac2);
        }

        @Test
        @DisplayName("不同密钥应产生不同 HMAC-SHA256")
        void shouldProduceDifferentHmacWithDifferentKeys() {
            String data = "same data";
            String hmac1 = HmacUtil.hmacSha256(data, key);
            String hmac2 = HmacUtil.hmacSha256(data, otherKey);
            assertNotEquals(hmac1, hmac2);
        }

        @Test
        @DisplayName("不同数据应产生不同 HMAC-SHA256")
        void shouldProduceDifferentHmacForDifferentData() {
            String hmac1 = HmacUtil.hmacSha256("data one", key);
            String hmac2 = HmacUtil.hmacSha256("data two", key);
            assertNotEquals(hmac1, hmac2);
        }

        @Test
        @DisplayName("HMAC-SHA256 值应仅包含小写十六进制字符")
        void shouldContainOnlyLowercaseHex() {
            String result = HmacUtil.hmacSha256("test", key);
            assertTrue(result.matches("^[0-9a-f]{64}$"),
                    "HMAC-SHA256 应为小写十六进制字符");
        }

        @Test
        @DisplayName("中文数据 HMAC-SHA256 计算应正常")
        void shouldHashChinese() {
            String result = HmacUtil.hmacSha256("你好，世界！", key);
            assertNotNull(result);
            assertEquals(64, result.length());
        }

        @Test
        @DisplayName("null 数据应返回 null")
        void shouldReturnNullForNullData() {
            assertNull(HmacUtil.hmacSha256(null, key));
        }

        @Test
        @DisplayName("null 密钥应返回 null")
        void shouldReturnNullForNullKey() {
            assertNull(HmacUtil.hmacSha256("data", null));
        }
    }

    // ===================== HMAC-SHA256（Base64）测试 =====================

    @Nested
    @DisplayName("HMAC-SHA256（Base64）测试")
    class HmacSha256Base64Tests {

        @Test
        @DisplayName("HMAC-SHA256 Base64 应返回非空字符串")
        void shouldReturnNonEmptyBase64() {
            String result = HmacUtil.hmacSha256Base64("hello", key);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("同一数据和密钥应产生相同 HMAC-SHA256 Base64")
        void shouldBeDeterministic() {
            String data = "base64 deterministic test";
            assertEquals(
                    HmacUtil.hmacSha256Base64(data, key),
                    HmacUtil.hmacSha256Base64(data, key));
        }

        @Test
        @DisplayName("不同密钥应产生不同 HMAC-SHA256 Base64")
        void shouldProduceDifferentHmacWithDifferentKeys() {
            String data = "same data";
            assertNotEquals(
                    HmacUtil.hmacSha256Base64(data, key),
                    HmacUtil.hmacSha256Base64(data, otherKey));
        }

        @Test
        @DisplayName("null 数据或 null 密钥应返回 null")
        void shouldReturnNullForNullInputs() {
            assertNull(HmacUtil.hmacSha256Base64(null, key));
            assertNull(HmacUtil.hmacSha256Base64("data", null));
        }
    }

    // ===================== HMAC-SHA512 测试 =====================

    @Nested
    @DisplayName("HMAC-SHA512 测试")
    class HmacSha512Tests {

        @Test
        @DisplayName("HMAC-SHA512 应返回 128 位十六进制字符串")
        void shouldReturn128HexChars() {
            String result = HmacUtil.hmacSha512("hello", key);
            assertNotNull(result);
            assertEquals(128, result.length(), "HMAC-SHA512 应返回 128 位十六进制字符串");
        }

        @Test
        @DisplayName("同一数据和密钥应产生相同 HMAC-SHA512")
        void shouldBeDeterministic() {
            String data = "sha512 deterministic";
            assertEquals(
                    HmacUtil.hmacSha512(data, key),
                    HmacUtil.hmacSha512(data, key));
        }

        @Test
        @DisplayName("HMAC-SHA512 与 HMAC-SHA256 同一输入应产生不同结果")
        void sha256AndSha512ShouldDiffer() {
            String data = "compare hmac algorithms";
            assertNotEquals(
                    HmacUtil.hmacSha256(data, key),
                    HmacUtil.hmacSha512(data, key));
        }

        @Test
        @DisplayName("null 数据或 null 密钥应返回 null")
        void shouldReturnNullForNullInputs() {
            assertNull(HmacUtil.hmacSha512(null, key));
            assertNull(HmacUtil.hmacSha512("data", null));
        }
    }

    // ===================== HMAC-SHA512（Base64）测试 =====================

    @Nested
    @DisplayName("HMAC-SHA512（Base64）测试")
    class HmacSha512Base64Tests {

        @Test
        @DisplayName("HMAC-SHA512 Base64 应返回非空字符串")
        void shouldReturnNonEmptyBase64() {
            String result = HmacUtil.hmacSha512Base64("hello", key);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("同一数据和密钥应产生相同 HMAC-SHA512 Base64")
        void shouldBeDeterministic() {
            String data = "sha512 base64 deterministic";
            assertEquals(
                    HmacUtil.hmacSha512Base64(data, key),
                    HmacUtil.hmacSha512Base64(data, key));
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullForNullInputs() {
            assertNull(HmacUtil.hmacSha512Base64(null, key));
            assertNull(HmacUtil.hmacSha512Base64("data", null));
        }
    }

    // ===================== 动态密钥测试 =====================

    @Nested
    @DisplayName("动态密钥测试")
    class DynamicKeyTests {

        @Test
        @DisplayName("使用 KeyGenerator 生成的 HMAC 密钥应能正常计算")
        void shouldWorkWithGeneratedKey() {
            String generatedKey = KeyGenerator.generateHmacKey();
            String result = HmacUtil.hmacSha256("test data", generatedKey);
            assertNotNull(result);
            assertEquals(64, result.length());
        }

        @Test
        @DisplayName("两个随机生成的密钥应产生不同 HMAC")
        void generatedKeysShouldProduceDifferentHmac() {
            String keyA = KeyGenerator.generateHmacKey();
            String keyB = KeyGenerator.generateHmacKey();
            assertNotEquals(
                    HmacUtil.hmacSha256("test", keyA),
                    HmacUtil.hmacSha256("test", keyB));
        }
    }
}
