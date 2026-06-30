package com.github.leyland.letool.cipher.sm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 国密 SM4 对称加密单元测试.
 */
@DisplayName("国密 SM4 对称加密测试")
class Sm4UtilTest {

    private static String sm4Key;
    private static String otherSm4Key;

    @BeforeAll
    static void setUpKeys() {
        sm4Key = Sm4Util.generateKey();
        otherSm4Key = Sm4Util.generateKey();
    }

    // ===================== 密钥生成测试 =====================

    @Nested
    @DisplayName("密钥生成测试")
    class KeyGenerationTests {

        @Test
        @DisplayName("生成 SM4 密钥应返回非空 Base64 字符串")
        void shouldGenerateSm4Key() {
            String key = Sm4Util.generateKey();
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }

        @Test
        @DisplayName("每次生成 SM4 密钥应互不相同")
        void shouldGenerateDifferentKeysEachTime() {
            String key1 = Sm4Util.generateKey();
            String key2 = Sm4Util.generateKey();
            assertNotEquals(key1, key2);
        }

        @Test
        @DisplayName("SM4 密钥 Base64 解码后应为 16 字节（128 位）")
        void sm4KeyShouldDecodeTo16Bytes() {
            String key = Sm4Util.generateKey();
            byte[] decoded = java.util.Base64.getDecoder().decode(key);
            assertEquals(16, decoded.length, "SM4 密钥应为 128 位（16 字节）");
        }
    }

    // ===================== 加密解密测试 =====================

    @Nested
    @DisplayName("加密解密测试")
    class EncryptDecryptTests {

        @Test
        @DisplayName("SM4 加密后解密应恢复原文")
        void shouldRoundtripSm4() {
            String plainText = "Hello SM4!";
            String encrypted = Sm4Util.encrypt(plainText, sm4Key);
            assertNotNull(encrypted);
            assertNotEquals(plainText, encrypted);

            String decrypted = Sm4Util.decrypt(encrypted, sm4Key);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("加密中文文本后解密应恢复原文")
        void shouldRoundtripChinese() {
            String plainText = "你好，世界！国密 SM4 对称加密测试";
            String encrypted = Sm4Util.encrypt(plainText, sm4Key);
            String decrypted = Sm4Util.decrypt(encrypted, sm4Key);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("加密长文本后解密应恢复原文")
        void shouldRoundtripLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("国密SM4长文本加解密测试-").append(i).append(";");
            }
            String plainText = sb.toString();
            String encrypted = Sm4Util.encrypt(plainText, sm4Key);
            String decrypted = Sm4Util.decrypt(encrypted, sm4Key);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("使用不同密钥加密同一明文应产生不同密文")
        void shouldProduceDifferentCiphertextWithDifferentKeys() {
            String plainText = "Same plaintext SM4";
            String enc1 = Sm4Util.encrypt(plainText, sm4Key);
            String enc2 = Sm4Util.encrypt(plainText, otherSm4Key);
            assertNotEquals(enc1, enc2);
        }

        @Test
        @DisplayName("同一密钥加密同一明文两次应产生不同密文（随机 IV）")
        void shouldProduceDifferentCiphertextForSameInput() {
            String plainText = "Same input twice";
            String enc1 = Sm4Util.encrypt(plainText, sm4Key);
            String enc2 = Sm4Util.encrypt(plainText, sm4Key);
            assertNotEquals(enc1, enc2);
        }

        @Test
        @DisplayName("使用错误密钥解密应抛出异常")
        void shouldThrowOnWrongKey() {
            String encrypted = Sm4Util.encrypt("secret", sm4Key);
            assertThrows(Exception.class, () -> Sm4Util.decrypt(encrypted, otherSm4Key));
        }
    }

    // ===================== 边界条件测试 =====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("加密 null 明文应返回 null")
        void shouldReturnNullForNullPlaintext() {
            assertNull(Sm4Util.encrypt(null, sm4Key));
        }

        @Test
        @DisplayName("解密 null 密文应返回 null")
        void shouldReturnNullForNullCiphertext() {
            assertNull(Sm4Util.decrypt(null, sm4Key));
        }

        @Test
        @DisplayName("加密空字符串后解密应恢复空字符串")
        void shouldRoundtripEmptyString() {
            String encrypted = Sm4Util.encrypt("", sm4Key);
            assertNotNull(encrypted);
            String decrypted = Sm4Util.decrypt(encrypted, sm4Key);
            assertEquals("", decrypted);
        }

        @Test
        @DisplayName("加密特殊字符文本后解密应恢复原文")
        void shouldRoundtripSpecialCharacters() {
            String plainText = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\\\n\t\r";
            String encrypted = Sm4Util.encrypt(plainText, sm4Key);
            String decrypted = Sm4Util.decrypt(encrypted, sm4Key);
            assertEquals(plainText, decrypted);
        }
    }
}
