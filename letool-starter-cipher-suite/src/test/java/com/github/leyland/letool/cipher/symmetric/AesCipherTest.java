package com.github.leyland.letool.cipher.symmetric;

import com.github.leyland.letool.cipher.model.CipherMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES 对称加密单元测试 —— 覆盖 GCM 和 CBC 模式.
 */
@DisplayName("AES 对称加密测试")
class AesCipherTest {

    private static String gcmKey128;
    private static String gcmKey256;
    private static String cbcKey128;
    private static String cbcKey256;

    @BeforeAll
    static void setUpKeys() {
        gcmKey128 = AesCipher.generateKey(128);
        gcmKey256 = AesCipher.generateKey(256);
        cbcKey128 = AesCipher.generateKey(128);
        cbcKey256 = AesCipher.generateKey(256);
    }

    // ===================== 密钥生成测试 =====================

    @Nested
    @DisplayName("密钥生成测试")
    class KeyGenerationTests {

        @Test
        @DisplayName("生成 128 位 AES 密钥应返回非空 Base64 字符串")
        void shouldGenerate128BitKey() {
            String key = AesCipher.generateKey(128);
            assertNotNull(key);
            assertFalse(key.isEmpty());
            assertEquals(24, key.length(), "128位密钥Base64编码后应为24字符");
        }

        @Test
        @DisplayName("生成 192 位 AES 密钥应返回非空 Base64 字符串")
        void shouldGenerate192BitKey() {
            String key = AesCipher.generateKey(192);
            assertNotNull(key);
            assertFalse(key.isEmpty());
            assertEquals(32, key.length(), "192位密钥Base64编码后应为32字符");
        }

        @Test
        @DisplayName("生成 256 位 AES 密钥应返回非空 Base64 字符串")
        void shouldGenerate256BitKey() {
            String key = AesCipher.generateKey(256);
            assertNotNull(key);
            assertFalse(key.isEmpty());
            assertEquals(44, key.length(), "256位密钥Base64编码后应为44字符");
        }

        @Test
        @DisplayName("每次生成密钥应互不相同")
        void shouldGenerateDifferentKeysEachTime() {
            String key1 = AesCipher.generateKey(128);
            String key2 = AesCipher.generateKey(128);
            assertNotEquals(key1, key2);
        }
    }

    // ===================== AES-GCM 加密解密测试 =====================

    @Nested
    @DisplayName("AES-GCM 加密解密测试")
    class GcmTests {

        @Test
        @DisplayName("GCM 默认模式加密后解密应恢复原文")
        void shouldRoundtripGcm() {
            String plainText = "Hello AES-GCM!";
            String encrypted = AesCipher.encrypt(plainText, gcmKey256);
            assertNotNull(encrypted);
            assertNotEquals(plainText, encrypted);

            String decrypted = AesCipher.decrypt(encrypted, gcmKey256);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("GCM 显式模式加密后解密应恢复原文")
        void shouldRoundtripGcmExplicit() {
            String plainText = "Explicit GCM mode test";
            String encrypted = AesCipher.encrypt(plainText, gcmKey128, CipherMode.GCM);
            String decrypted = AesCipher.decrypt(encrypted, gcmKey128);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("GCM 加密中文文本后解密应恢复原文")
        void shouldRoundtripChineseGcm() {
            String plainText = "你好，世界！AES-GCM 加密测试";
            String encrypted = AesCipher.encrypt(plainText, gcmKey256);
            String decrypted = AesCipher.decrypt(encrypted, gcmKey256);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("GCM 加密长文本后解密应恢复原文")
        void shouldRoundtripLongTextGcm() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("长文本测试LongTextTest-").append(i).append(";");
            }
            String plainText = sb.toString();
            String encrypted = AesCipher.encrypt(plainText, gcmKey256);
            String decrypted = AesCipher.decrypt(encrypted, gcmKey256);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("使用不同密钥加密同一明文应产生不同密文")
        void shouldProduceDifferentCiphertextWithDifferentKeys() {
            String plainText = "Same plaintext";
            String otherKey = AesCipher.generateKey(256);
            String enc1 = AesCipher.encrypt(plainText, gcmKey256);
            String enc2 = AesCipher.encrypt(plainText, otherKey);
            assertNotEquals(enc1, enc2);
        }

        @Test
        @DisplayName("同一密钥加密同一明文两次应产生不同密文（随机IV）")
        void shouldProduceDifferentCiphertextForSameInput() {
            String plainText = "Same input twice";
            String enc1 = AesCipher.encrypt(plainText, gcmKey256);
            String enc2 = AesCipher.encrypt(plainText, gcmKey256);
            assertNotEquals(enc1, enc2);
        }
    }

    // ===================== AES-CBC 加密解密测试 =====================

    @Nested
    @DisplayName("AES-CBC 加密解密测试")
    class CbcTests {

        @Test
        @DisplayName("CBC 模式加密后解密应恢复原文")
        void shouldRoundtripCbc() {
            String plainText = "Hello AES-CBC!";
            String encrypted = AesCipher.encrypt(plainText, cbcKey256, CipherMode.CBC);
            assertNotNull(encrypted);

            // decrypt auto-detects CBC
            String decrypted = AesCipher.decrypt(encrypted, cbcKey256);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("CBC 加密中文文本后解密应恢复原文")
        void shouldRoundtripChineseCbc() {
            String plainText = "你好，世界！AES-CBC 加密测试";
            String encrypted = AesCipher.encrypt(plainText, cbcKey128, CipherMode.CBC);
            String decrypted = AesCipher.decrypt(encrypted, cbcKey128);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("CBC 模式使用不同密钥应产生不同密文")
        void shouldProduceDifferentCiphertextWithDifferentKeysCbc() {
            String plainText = "Same plaintext CBC";
            String otherKey = AesCipher.generateKey(128);
            String enc1 = AesCipher.encrypt(plainText, cbcKey128, CipherMode.CBC);
            String enc2 = AesCipher.encrypt(plainText, otherKey, CipherMode.CBC);
            assertNotEquals(enc1, enc2);
        }
    }

    // ===================== 多模式混合测试 =====================

    @Nested
    @DisplayName("多模式混合测试")
    class MixedModeTests {

        @Test
        @DisplayName("同一密钥用于 GCM 和 CBC 加密同一明文应产生不同密文")
        void shouldProduceDifferentCiphertextForGcmAndCbc() {
            String plainText = "Mixed mode test";
            String key = AesCipher.generateKey(128);
            String gcmEnc = AesCipher.encrypt(plainText, key, CipherMode.GCM);
            String cbcEnc = AesCipher.encrypt(plainText, key, CipherMode.CBC);
            assertNotEquals(gcmEnc, cbcEnc);
        }

        @Test
        @DisplayName("GCM 密文用错误密钥解密应抛出异常")
        void shouldThrowOnWrongKeyGcm() {
            String encrypted = AesCipher.encrypt("secret", gcmKey256);
            String wrongKey = AesCipher.generateKey(256);
            assertThrows(Exception.class, () -> AesCipher.decrypt(encrypted, wrongKey));
        }

        @Test
        @DisplayName("CBC 密文用错误密钥解密应抛出异常")
        void shouldThrowOnWrongKeyCbc() {
            String encrypted = AesCipher.encrypt("secret", cbcKey128, CipherMode.CBC);
            String wrongKey = AesCipher.generateKey(128);
            assertThrows(Exception.class, () -> AesCipher.decrypt(encrypted, wrongKey));
        }
    }

    // ===================== 边界条件测试 =====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("加密 null 明文应返回 null")
        void shouldReturnNullForNullPlaintext() {
            assertNull(AesCipher.encrypt(null, gcmKey256));
            assertNull(AesCipher.encrypt(null, gcmKey256, CipherMode.CBC));
        }

        @Test
        @DisplayName("解密 null 密文应返回 null")
        void shouldReturnNullForNullCiphertext() {
            assertNull(AesCipher.decrypt(null, gcmKey256));
        }

        @Test
        @DisplayName("加密空字符串后解密应恢复空字符串")
        void shouldRoundtripEmptyString() {
            String encrypted = AesCipher.encrypt("", gcmKey256);
            assertNotNull(encrypted);
            String decrypted = AesCipher.decrypt(encrypted, gcmKey256);
            assertEquals("", decrypted);
        }

        @Test
        @DisplayName("加密特殊字符文本后解密应恢复原文")
        void shouldRoundtripSpecialCharacters() {
            String plainText = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\\\n\t\r";
            String encrypted = AesCipher.encrypt(plainText, gcmKey256);
            String decrypted = AesCipher.decrypt(encrypted, gcmKey256);
            assertEquals(plainText, decrypted);
        }
    }
}
