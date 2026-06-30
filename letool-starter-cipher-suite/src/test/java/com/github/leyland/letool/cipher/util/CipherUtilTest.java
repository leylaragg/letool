package com.github.leyland.letool.cipher.util;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import com.github.leyland.letool.cipher.model.CipherMode;
import com.github.leyland.letool.cipher.sm.Sm2Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CipherUtil 统一入口单元测试 —— 验证所有门面方法正确委托.
 */
@DisplayName("CipherUtil 统一入口测试")
class CipherUtilTest {

    private static String aesKey;
    private static RsaCipher.RsaKeyPair rsaKeyPair;
    private static String hmacKey;
    private static String sm4Key;
    private static Sm2Util.Sm2KeyPair sm2KeyPair;

    @BeforeAll
    static void setUp() {
        aesKey = CipherUtil.generateAesKey(256);
        rsaKeyPair = CipherUtil.generateRsaKeyPair(2048);
        hmacKey = CipherUtil.generateHmacKey();
        sm4Key = CipherUtil.generateSm4Key();
        sm2KeyPair = CipherUtil.generateSm2KeyPair();
    }

    // ===================== AES 门面测试 =====================

    @Nested
    @DisplayName("AES 门面测试")
    class AesFacadeTests {

        @Test
        @DisplayName("aesEncrypt / aesDecrypt 默认 GCM 往返应成功")
        void shouldRoundtripAesGcmDefault() {
            String plainText = "CipherUtil AES GCM roundtrip";
            String encrypted = CipherUtil.aesEncrypt(plainText, aesKey);
            String decrypted = CipherUtil.aesDecrypt(encrypted, aesKey);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("aesEncrypt 指定 GCM 模式往返应成功")
        void shouldRoundtripAesGcmExplicit() {
            String plainText = "CipherUtil AES GCM explicit";
            String encrypted = CipherUtil.aesEncrypt(plainText, aesKey, CipherMode.GCM);
            String decrypted = CipherUtil.aesDecrypt(encrypted, aesKey);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("aesEncrypt 指定 CBC 模式往返应成功")
        void shouldRoundtripAesCbc() {
            String plainText = "CipherUtil AES CBC";
            String encrypted = CipherUtil.aesEncrypt(plainText, aesKey, CipherMode.CBC);
            String decrypted = CipherUtil.aesDecrypt(encrypted, aesKey);
            assertEquals(plainText, decrypted);
        }
    }

    // ===================== RSA 门面测试 =====================

    @Nested
    @DisplayName("RSA 门面测试")
    class RsaFacadeTests {

        @Test
        @DisplayName("rsaEncrypt / rsaDecrypt 往返应成功")
        void shouldRoundtripRsa() {
            String plainText = "CipherUtil RSA roundtrip";
            String encrypted = CipherUtil.rsaEncrypt(plainText, rsaKeyPair.getPublicKey());
            String decrypted = CipherUtil.rsaDecrypt(encrypted, rsaKeyPair.getPrivateKey());
            assertEquals(plainText, decrypted);
        }
    }

    // ===================== 哈希门面测试 =====================

    @Nested
    @DisplayName("哈希门面测试")
    class HashFacadeTests {

        @Test
        @DisplayName("md5 应返回 32 位十六进制字符串")
        void md5ShouldReturn32HexChars() {
            String hash = CipherUtil.md5("test");
            assertNotNull(hash);
            assertEquals(32, hash.length());
        }

        @Test
        @DisplayName("sha256 应返回 64 位十六进制字符串")
        void sha256ShouldReturn64HexChars() {
            String hash = CipherUtil.sha256("test");
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("sha512 应返回 128 位十六进制字符串")
        void sha512ShouldReturn128HexChars() {
            String hash = CipherUtil.sha512("test");
            assertNotNull(hash);
            assertEquals(128, hash.length());
        }

        @Test
        @DisplayName("同一输入 md5 多次计算应一致")
        void md5ShouldBeDeterministic() {
            assertEquals(CipherUtil.md5("test"), CipherUtil.md5("test"));
        }

        @Test
        @DisplayName("同一输入 sha256 多次计算应一致")
        void sha256ShouldBeDeterministic() {
            assertEquals(CipherUtil.sha256("test"), CipherUtil.sha256("test"));
        }

        @Test
        @DisplayName("同一输入 sha512 多次计算应一致")
        void sha512ShouldBeDeterministic() {
            assertEquals(CipherUtil.sha512("test"), CipherUtil.sha512("test"));
        }
    }

    // ===================== HMAC 门面测试 =====================

    @Nested
    @DisplayName("HMAC 门面测试")
    class HmacFacadeTests {

        @Test
        @DisplayName("hmacSha256 应返回 64 位十六进制字符串")
        void hmacSha256ShouldReturn64HexChars() {
            String result = CipherUtil.hmacSha256("test", hmacKey);
            assertNotNull(result);
            assertEquals(64, result.length());
        }

        @Test
        @DisplayName("hmacSha256 同一输入应一致")
        void hmacSha256ShouldBeDeterministic() {
            String key = "fixed-key";
            assertEquals(
                    CipherUtil.hmacSha256("test", key),
                    CipherUtil.hmacSha256("test", key));
        }

        @Test
        @DisplayName("hmacSha256Base64 应返回非空 Base64 字符串")
        void hmacSha256Base64ShouldReturnNonEmpty() {
            String result = CipherUtil.hmacSha256Base64("test", hmacKey);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("hmacSha512 应返回 128 位十六进制字符串")
        void hmacSha512ShouldReturn128HexChars() {
            String result = CipherUtil.hmacSha512("test", hmacKey);
            assertNotNull(result);
            assertEquals(128, result.length());
        }
    }

    // ===================== 数字签名门面测试 =====================

    @Nested
    @DisplayName("数字签名门面测试")
    class SignatureFacadeTests {

        @Test
        @DisplayName("sign / verify 往返应成功")
        void shouldRoundtripSignAndVerify() {
            String data = "CipherUtil sign-verify roundtrip";
            String signature = CipherUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertTrue(CipherUtil.verify(data, signature, rsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("sign 指定算法 / verify 指定算法往返应成功")
        void shouldRoundtripWithExplicitAlgorithm() {
            String data = "CipherUtil sign-verify with algo";
            String signature = CipherUtil.sign(data, rsaKeyPair.getPrivateKey(), "SHA512withRSA");
            assertTrue(CipherUtil.verify(data, signature, rsaKeyPair.getPublicKey(), "SHA512withRSA"));
        }

        @Test
        @DisplayName("数据被篡改后 verify 应返回 false")
        void shouldRejectTamperedData() {
            String data = "original data";
            String signature = CipherUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertFalse(CipherUtil.verify("tampered data", signature, rsaKeyPair.getPublicKey()));
        }
    }

    // ===================== 密钥生成门面测试 =====================

    @Nested
    @DisplayName("密钥生成门面测试")
    class KeyGenerationFacadeTests {

        @Test
        @DisplayName("generateAesKey 应返回非空密钥")
        void generateAesKeyShouldReturnNonEmpty() {
            String key = CipherUtil.generateAesKey(128);
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }

        @Test
        @DisplayName("generateRsaKeyPair 应返回有效密钥对")
        void generateRsaKeyPairShouldReturnValidPair() {
            RsaCipher.RsaKeyPair pair = CipherUtil.generateRsaKeyPair(2048);
            assertNotNull(pair.getPublicKey());
            assertNotNull(pair.getPrivateKey());
        }

        @Test
        @DisplayName("generateHmacKey 应返回非空密钥")
        void generateHmacKeyShouldReturnNonEmpty() {
            String key = CipherUtil.generateHmacKey();
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }
    }

    // ===================== 国密算法门面测试 =====================

    @Nested
    @DisplayName("国密算法门面测试")
    class SmFacadeTests {

        @Test
        @DisplayName("sm3 应返回 64 位十六进制字符串")
        void sm3ShouldReturn64HexChars() {
            String hash = CipherUtil.sm3("国密测试");
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("sm3 同一输入应一致")
        void sm3ShouldBeDeterministic() {
            assertEquals(CipherUtil.sm3("test"), CipherUtil.sm3("test"));
        }

        @Test
        @DisplayName("generateSm4Key 应返回非空密钥")
        void generateSm4KeyShouldReturnNonEmpty() {
            String key = CipherUtil.generateSm4Key();
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }

        @Test
        @DisplayName("sm4Encrypt / sm4Decrypt 往返应成功")
        void shouldRoundtripSm4() {
            String plainText = "CipherUtil SM4 roundtrip";
            String encrypted = CipherUtil.sm4Encrypt(plainText, sm4Key);
            String decrypted = CipherUtil.sm4Decrypt(encrypted, sm4Key);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("generateSm2KeyPair 应返回有效密钥对")
        void generateSm2KeyPairShouldReturnValidPair() {
            Sm2Util.Sm2KeyPair pair = CipherUtil.generateSm2KeyPair();
            assertNotNull(pair.getPublicKey());
            assertNotNull(pair.getPrivateKey());
        }

        @Test
        @DisplayName("sm2Encrypt / sm2Decrypt 往返应成功")
        void shouldRoundtripSm2() {
            String plainText = "CipherUtil SM2 roundtrip";
            String encrypted = CipherUtil.sm2Encrypt(plainText, sm2KeyPair.getPublicKey());
            String decrypted = CipherUtil.sm2Decrypt(encrypted, sm2KeyPair.getPrivateKey());
            assertEquals(plainText, decrypted);
        }
    }
}
