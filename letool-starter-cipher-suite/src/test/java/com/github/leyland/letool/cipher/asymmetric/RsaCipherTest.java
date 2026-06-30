package com.github.leyland.letool.cipher.asymmetric;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RSA 非对称加密单元测试.
 */
@DisplayName("RSA 非对称加密测试")
class RsaCipherTest {

    private static RsaCipher.RsaKeyPair keyPair2048;
    private static RsaCipher.RsaKeyPair otherKeyPair;

    @BeforeAll
    static void setUpKeys() {
        keyPair2048 = RsaCipher.generateKeyPair(2048);
        otherKeyPair = RsaCipher.generateKeyPair(2048);
    }

    // ===================== 密钥对生成测试 =====================

    @Nested
    @DisplayName("密钥对生成测试")
    class KeyPairGenerationTests {

        @Test
        @DisplayName("生成 2048 位 RSA 密钥对应返回非空公钥和私钥")
        void shouldGenerateRsa2048KeyPair() {
            RsaCipher.RsaKeyPair pair = RsaCipher.generateKeyPair(2048);
            assertNotNull(pair);
            assertNotNull(pair.getPublicKey());
            assertFalse(pair.getPublicKey().isEmpty());
            assertNotNull(pair.getPrivateKey());
            assertFalse(pair.getPrivateKey().isEmpty());
        }

        @Test
        @DisplayName("RsaKeyPair 的公钥和私钥应互不相同")
        void publicAndPrivateKeyShouldDiffer() {
            assertNotEquals(keyPair2048.getPublicKey(), keyPair2048.getPrivateKey());
        }

        @Test
        @DisplayName("每次生成密钥对应互不相同")
        void shouldGenerateDifferentKeyPairsEachTime() {
            RsaCipher.RsaKeyPair pair1 = RsaCipher.generateKeyPair(2048);
            RsaCipher.RsaKeyPair pair2 = RsaCipher.generateKeyPair(2048);
            assertNotEquals(pair1.getPublicKey(), pair2.getPublicKey());
            assertNotEquals(pair1.getPrivateKey(), pair2.getPrivateKey());
        }

        @Test
        @DisplayName("生成 1024 位密钥对应成功")
        void shouldGenerate1024BitKeyPair() {
            RsaCipher.RsaKeyPair pair = RsaCipher.generateKeyPair(1024);
            assertNotNull(pair.getPublicKey());
            assertNotNull(pair.getPrivateKey());
        }
    }

    // ===================== 加密解密测试 =====================

    @Nested
    @DisplayName("加密解密测试")
    class EncryptDecryptTests {

        @Test
        @DisplayName("公钥加密后私钥解密应恢复原文")
        void shouldRoundtripRsa() {
            String plainText = "Hello RSA!";
            String encrypted = RsaCipher.encrypt(plainText, keyPair2048.getPublicKey());
            assertNotNull(encrypted);
            assertNotEquals(plainText, encrypted);

            String decrypted = RsaCipher.decrypt(encrypted, keyPair2048.getPrivateKey());
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("加密中文文本后解密应恢复原文")
        void shouldRoundtripChinese() {
            String plainText = "你好，世界！RSA 非对称加密测试";
            String encrypted = RsaCipher.encrypt(plainText, keyPair2048.getPublicKey());
            String decrypted = RsaCipher.decrypt(encrypted, keyPair2048.getPrivateKey());
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("加密特殊字符文本后解密应恢复原文")
        void shouldRoundtripSpecialCharacters() {
            String plainText = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
            String encrypted = RsaCipher.encrypt(plainText, keyPair2048.getPublicKey());
            String decrypted = RsaCipher.decrypt(encrypted, keyPair2048.getPrivateKey());
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("使用不同公钥加密同一明文应产生不同密文")
        void shouldProduceDifferentCiphertextWithDifferentPublicKeys() {
            String plainText = "Same plaintext";
            String enc1 = RsaCipher.encrypt(plainText, keyPair2048.getPublicKey());
            String enc2 = RsaCipher.encrypt(plainText, otherKeyPair.getPublicKey());
            assertNotEquals(enc1, enc2);
        }

        @Test
        @DisplayName("使用同一公钥加密同一明文两次应产生不同密文（随机填充）")
        void shouldProduceDifferentCiphertextForSameInput() {
            String plainText = "Same input twice";
            String enc1 = RsaCipher.encrypt(plainText, keyPair2048.getPublicKey());
            String enc2 = RsaCipher.encrypt(plainText, keyPair2048.getPublicKey());
            assertNotEquals(enc1, enc2, "PKCS1随机填充应使每次加密结果不同");
        }
    }

    // ===================== 异常和错误场景测试 =====================

    @Nested
    @DisplayName("异常和错误场景测试")
    class ErrorScenarioTests {

        @Test
        @DisplayName("用错误私钥解密应抛出异常")
        void shouldThrowOnWrongPrivateKey() {
            String encrypted = RsaCipher.encrypt("secret", keyPair2048.getPublicKey());
            assertThrows(Exception.class,
                    () -> RsaCipher.decrypt(encrypted, otherKeyPair.getPrivateKey()));
        }

        @Test
        @DisplayName("尝试用私钥加密格式的密钥进行加密应抛出异常")
        void shouldThrowOnInvalidPublicKey() {
            assertThrows(Exception.class,
                    () -> RsaCipher.encrypt("test", "invalid-base64-key!!!"));
        }

        @Test
        @DisplayName("尝试解密格式错误的密文应抛出异常")
        void shouldThrowOnInvalidCiphertext() {
            assertThrows(Exception.class,
                    () -> RsaCipher.decrypt("not-valid-base64!!!", keyPair2048.getPrivateKey()));
        }
    }

    // ===================== 边界条件测试 =====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("加密 null 明文应返回 null")
        void shouldReturnNullForNullPlaintext() {
            assertNull(RsaCipher.encrypt(null, keyPair2048.getPublicKey()));
        }

        @Test
        @DisplayName("解密 null 密文应返回 null")
        void shouldReturnNullForNullCiphertext() {
            assertNull(RsaCipher.decrypt(null, keyPair2048.getPrivateKey()));
        }
    }
}
