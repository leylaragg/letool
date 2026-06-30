package com.github.leyland.letool.cipher.key;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密钥生成工具单元测试.
 */
@DisplayName("密钥生成工具测试")
class KeyGeneratorTest {

    // ===================== AES 密钥生成测试 =====================

    @Nested
    @DisplayName("AES 密钥生成测试")
    class AesKeyGenerationTests {

        @Test
        @DisplayName("生成 128 位 AES 密钥应返回非空 Base64 字符串")
        void shouldGenerate128BitAesKey() {
            String key = KeyGenerator.generateAesKey(128);
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }

        @Test
        @DisplayName("生成 192 位 AES 密钥应返回非空 Base64 字符串")
        void shouldGenerate192BitAesKey() {
            String key = KeyGenerator.generateAesKey(192);
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }

        @Test
        @DisplayName("生成 256 位 AES 密钥应返回非空 Base64 字符串")
        void shouldGenerate256BitAesKey() {
            String key = KeyGenerator.generateAesKey(256);
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }

        @Test
        @DisplayName("每次生成的 AES 密钥应互不相同")
        void shouldGenerateDifferentAesKeysEachTime() {
            String key1 = KeyGenerator.generateAesKey(256);
            String key2 = KeyGenerator.generateAesKey(256);
            assertNotEquals(key1, key2);
        }

        @Test
        @DisplayName("不同位数的 AES 密钥长度应不同")
        void differentSizeKeysShouldHaveDifferentLength() {
            String key128 = KeyGenerator.generateAesKey(128);
            String key256 = KeyGenerator.generateAesKey(256);
            assertNotEquals(key128.length(), key256.length());
        }
    }

    // ===================== RSA 密钥对生成测试 =====================

    @Nested
    @DisplayName("RSA 密钥对生成测试")
    class RsaKeyPairGenerationTests {

        @Test
        @DisplayName("生成 2048 位 RSA 密钥对应返回公钥和私钥")
        void shouldGenerate2048BitRsaKeyPair() {
            RsaCipher.RsaKeyPair pair = KeyGenerator.generateRsaKeyPair(2048);
            assertNotNull(pair);
            assertNotNull(pair.getPublicKey());
            assertFalse(pair.getPublicKey().isEmpty());
            assertNotNull(pair.getPrivateKey());
            assertFalse(pair.getPrivateKey().isEmpty());
        }

        @Test
        @DisplayName("RSA 公钥和私钥应互不相同")
        void publicKeyAndPrivateKeyShouldDiffer() {
            RsaCipher.RsaKeyPair pair = KeyGenerator.generateRsaKeyPair(2048);
            assertNotEquals(pair.getPublicKey(), pair.getPrivateKey());
        }

        @Test
        @DisplayName("每次生成 RSA 密钥对应互不相同")
        void shouldGenerateDifferentKeyPairs() {
            RsaCipher.RsaKeyPair pair1 = KeyGenerator.generateRsaKeyPair(2048);
            RsaCipher.RsaKeyPair pair2 = KeyGenerator.generateRsaKeyPair(2048);
            assertNotEquals(pair1.getPublicKey(), pair2.getPublicKey());
            assertNotEquals(pair1.getPrivateKey(), pair2.getPrivateKey());
        }

        @Test
        @DisplayName("生成 1024 位和 4096 位密钥对应成功")
        void shouldGenerateVariousSizeKeyPairs() {
            RsaCipher.RsaKeyPair pair1024 = KeyGenerator.generateRsaKeyPair(1024);
            assertNotNull(pair1024.getPublicKey());

            RsaCipher.RsaKeyPair pair4096 = KeyGenerator.generateRsaKeyPair(4096);
            assertNotNull(pair4096.getPublicKey());
        }
    }

    // ===================== HMAC 密钥生成测试 =====================

    @Nested
    @DisplayName("HMAC 密钥生成测试")
    class HmacKeyGenerationTests {

        @Test
        @DisplayName("生成 HMAC 密钥应返回非空 Base64 字符串")
        void shouldGenerateHmacKey() {
            String key = KeyGenerator.generateHmacKey();
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }

        @Test
        @DisplayName("每次生成的 HMAC 密钥应互不相同")
        void shouldGenerateDifferentHmacKeys() {
            String key1 = KeyGenerator.generateHmacKey();
            String key2 = KeyGenerator.generateHmacKey();
            assertNotEquals(key1, key2);
        }

        @Test
        @DisplayName("HMAC 密钥 Base64 解码后应为 32 字节")
        void hmacKeyShouldDecodeTo32Bytes() {
            String key = KeyGenerator.generateHmacKey();
            byte[] decoded = java.util.Base64.getDecoder().decode(key);
            assertEquals(32, decoded.length, "HMAC 密钥应为 32 字节");
        }
    }
}
