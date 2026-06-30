package com.github.leyland.letool.cipher.sm;

import com.github.leyland.letool.cipher.exception.CipherException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 国密 SM2 非对称加密单元测试.
 */
@DisplayName("国密 SM2 非对称加密测试")
class Sm2UtilTest {

    private static Sm2Util.Sm2KeyPair keyPair;
    private static Sm2Util.Sm2KeyPair otherKeyPair;

    @BeforeAll
    static void setUpKeys() {
        keyPair = Sm2Util.generateKeyPair();
        otherKeyPair = Sm2Util.generateKeyPair();
    }

    // ===================== 密钥对生成测试 =====================

    @Nested
    @DisplayName("密钥对生成测试")
    class KeyPairGenerationTests {

        @Test
        @DisplayName("生成 SM2 密钥对应返回非空公钥和私钥")
        void shouldGenerateSm2KeyPair() {
            Sm2Util.Sm2KeyPair pair = Sm2Util.generateKeyPair();
            assertNotNull(pair);
            assertNotNull(pair.getPublicKey());
            assertFalse(pair.getPublicKey().isEmpty());
            assertNotNull(pair.getPrivateKey());
            assertFalse(pair.getPrivateKey().isEmpty());
        }

        @Test
        @DisplayName("SM2 公钥和私钥应互不相同")
        void publicKeyAndPrivateKeyShouldDiffer() {
            assertNotEquals(keyPair.getPublicKey(), keyPair.getPrivateKey());
        }

        @Test
        @DisplayName("每次生成的 SM2 密钥对应互不相同")
        void shouldGenerateDifferentKeyPairs() {
            Sm2Util.Sm2KeyPair pair1 = Sm2Util.generateKeyPair();
            Sm2Util.Sm2KeyPair pair2 = Sm2Util.generateKeyPair();
            assertNotEquals(pair1.getPublicKey(), pair2.getPublicKey());
            assertNotEquals(pair1.getPrivateKey(), pair2.getPrivateKey());
        }
    }

    // ===================== 加密解密测试 =====================

    @Nested
    @DisplayName("加密解密测试")
    class EncryptDecryptTests {

        @Test
        @DisplayName("公钥加密后私钥解密应恢复原文")
        void shouldRoundtripSm2() {
            String plainText = "Hello SM2!";
            String encrypted = Sm2Util.encrypt(plainText, keyPair.getPublicKey());
            assertNotNull(encrypted);
            assertNotEquals(plainText, encrypted);

            String decrypted = Sm2Util.decrypt(encrypted, keyPair.getPrivateKey());
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("加密中文文本后解密应恢复原文")
        void shouldRoundtripChinese() {
            String plainText = "你好，世界！国密 SM2 非对称加密测试";
            String encrypted = Sm2Util.encrypt(plainText, keyPair.getPublicKey());
            String decrypted = Sm2Util.decrypt(encrypted, keyPair.getPrivateKey());
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("加密特殊字符文本后解密应恢复原文")
        void shouldRoundtripSpecialCharacters() {
            String plainText = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
            String encrypted = Sm2Util.encrypt(plainText, keyPair.getPublicKey());
            String decrypted = Sm2Util.decrypt(encrypted, keyPair.getPrivateKey());
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("使用不同公钥加密同一明文应产生不同密文")
        void shouldProduceDifferentCiphertextWithDifferentPublicKeys() {
            String plainText = "Same plaintext SM2";
            String enc1 = Sm2Util.encrypt(plainText, keyPair.getPublicKey());
            String enc2 = Sm2Util.encrypt(plainText, otherKeyPair.getPublicKey());
            assertNotEquals(enc1, enc2);
        }

        @Test
        @DisplayName("使用错误私钥解密应抛出异常")
        void shouldThrowOnWrongPrivateKey() {
            String encrypted = Sm2Util.encrypt("secret", keyPair.getPublicKey());
            assertThrows(Exception.class,
                    () -> Sm2Util.decrypt(encrypted, otherKeyPair.getPrivateKey()));
        }
    }

    // ===================== 边界条件测试 =====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("加密 null 明文应返回 null")
        void shouldReturnNullForNullPlaintext() {
            assertNull(Sm2Util.encrypt(null, keyPair.getPublicKey()));
        }

        @Test
        @DisplayName("解密 null 密文应返回 null")
        void shouldReturnNullForNullCiphertext() {
            assertNull(Sm2Util.decrypt(null, keyPair.getPrivateKey()));
        }

        @Test
        @DisplayName("加密空字符串应抛出异常")
        void shouldThrowOnEmptyString() {
            assertThrows(CipherException.class,
                    () -> Sm2Util.encrypt("", keyPair.getPublicKey()));
        }
    }
}
