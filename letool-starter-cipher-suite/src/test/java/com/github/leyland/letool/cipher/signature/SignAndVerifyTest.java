package com.github.leyland.letool.cipher.signature;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import com.github.leyland.letool.cipher.exception.CipherException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数字签名与验签单元测试 —— 同时覆盖 SignUtil 和 VerifyUtil.
 */
@DisplayName("数字签名与验签测试")
class SignAndVerifyTest {

    private static RsaCipher.RsaKeyPair rsaKeyPair;
    private static RsaCipher.RsaKeyPair otherRsaKeyPair;

    @BeforeAll
    static void setUpKeys() {
        rsaKeyPair = RsaCipher.generateKeyPair(2048);
        otherRsaKeyPair = RsaCipher.generateKeyPair(2048);
    }

    // ===================== 签名测试 (SignUtil) =====================

    @Nested
    @DisplayName("签名测试 (SignUtil)")
    class SignTests {

        @Test
        @DisplayName("使用 RSA 私钥签名应返回非空 Base64 字符串")
        void shouldReturnNonEmptySignature() {
            String signature = SignUtil.sign("Hello Sign!", rsaKeyPair.getPrivateKey());
            assertNotNull(signature);
            assertFalse(signature.isEmpty());
        }

        @Test
        @DisplayName("同一数据和私钥应产生相同签名")
        void shouldBeDeterministic() {
            String data = "deterministic sign test";
            String sig1 = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            String sig2 = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertEquals(sig1, sig2);
        }

        @Test
        @DisplayName("不同数据应产生不同签名")
        void shouldProduceDifferentSignatureForDifferentData() {
            String sig1 = SignUtil.sign("data one", rsaKeyPair.getPrivateKey());
            String sig2 = SignUtil.sign("data two", rsaKeyPair.getPrivateKey());
            assertNotEquals(sig1, sig2);
        }

        @Test
        @DisplayName("不同私钥应产生不同签名")
        void shouldProduceDifferentSignatureWithDifferentPrivateKeys() {
            String data = "same data";
            String sig1 = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            String sig2 = SignUtil.sign(data, otherRsaKeyPair.getPrivateKey());
            assertNotEquals(sig1, sig2);
        }

        @Test
        @DisplayName("使用 SHA256withRSA 算法签名应成功")
        void shouldSignWithSha256WithRsa() {
            String signature = SignUtil.sign("data", rsaKeyPair.getPrivateKey(), "SHA256withRSA");
            assertNotNull(signature);
            assertFalse(signature.isEmpty());
        }

        @Test
        @DisplayName("使用 SHA512withRSA 算法签名应成功")
        void shouldSignWithSha512WithRsa() {
            String signature = SignUtil.sign("data", rsaKeyPair.getPrivateKey(), "SHA512withRSA");
            assertNotNull(signature);
            assertFalse(signature.isEmpty());
        }

        @Test
        @DisplayName("签名中文数据应返回非空结果")
        void shouldSignChinese() {
            String signature = SignUtil.sign("你好，数字签名测试！", rsaKeyPair.getPrivateKey());
            assertNotNull(signature);
            assertFalse(signature.isEmpty());
        }

        @Test
        @DisplayName("null 数据签名应返回 null")
        void shouldReturnNullForNullData() {
            assertNull(SignUtil.sign(null, rsaKeyPair.getPrivateKey()));
        }

        @Test
        @DisplayName("null 私钥签名应返回 null")
        void shouldReturnNullForNullPrivateKey() {
            assertNull(SignUtil.sign("data", null));
        }
    }

    // ===================== 验签测试 (VerifyUtil) =====================

    @Nested
    @DisplayName("验签测试 (VerifyUtil)")
    class VerifyTests {

        @Test
        @DisplayName("正确公钥验签应返回 true")
        void shouldVerifyValidSignature() {
            String data = "verify test data";
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertTrue(VerifyUtil.verify(data, signature, rsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("数据被篡改后验签应返回 false")
        void shouldRejectTamperedData() {
            String data = "original data";
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertFalse(VerifyUtil.verify("tampered data", signature, rsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("使用错误公钥验签应返回 false")
        void shouldRejectWrongPublicKey() {
            String data = "test data";
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertFalse(VerifyUtil.verify(data, signature, otherRsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("签名被篡改后验签应抛出异常")
        void shouldRejectTamperedSignature() {
            String data = "test data";
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            String tamperedSig = signature.substring(0, signature.length() - 4) + "XXXX";
            assertThrows(CipherException.class,
                    () -> VerifyUtil.verify(data, tamperedSig, rsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("指定算法 SHA256withRSA 验签应成功")
        void shouldVerifyWithSpecificAlgorithm() {
            String data = "algo specific test";
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey(), "SHA256withRSA");
            assertTrue(VerifyUtil.verify(data, signature, rsaKeyPair.getPublicKey(), "SHA256withRSA"));
        }

        @Test
        @DisplayName("签名算法与验签算法不一致应返回 false")
        void shouldRejectMismatchedAlgorithm() {
            String data = "algo mismatch test";
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey(), "SHA256withRSA");
            assertFalse(VerifyUtil.verify(data, signature, rsaKeyPair.getPublicKey(), "SHA512withRSA"));
        }

        @Test
        @DisplayName("null 数据验签应返回 false")
        void shouldReturnFalseForNullData() {
            String signature = SignUtil.sign("data", rsaKeyPair.getPrivateKey());
            assertFalse(VerifyUtil.verify(null, signature, rsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("null 签名验签应返回 false")
        void shouldReturnFalseForNullSignature() {
            assertFalse(VerifyUtil.verify("data", null, rsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("null 公钥验签应返回 false")
        void shouldReturnFalseForNullPublicKey() {
            String signature = SignUtil.sign("data", rsaKeyPair.getPrivateKey());
            assertFalse(VerifyUtil.verify("data", signature, null));
        }
    }

    // ===================== 签名验签完整往返测试 =====================

    @Nested
    @DisplayName("签名验签往返测试")
    class SignVerifyRoundtripTests {

        @Test
        @DisplayName("签名-验签完整往返应成功")
        void shouldRoundtripSignAndVerify() {
            String data = "roundtrip sign-verify";
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertTrue(VerifyUtil.verify(data, signature, rsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("中文数据签名-验签完整往返应成功")
        void shouldRoundtripChinese() {
            String data = "你好，数字签名往返测试！";
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertTrue(VerifyUtil.verify(data, signature, rsaKeyPair.getPublicKey()));
        }

        @Test
        @DisplayName("长数据签名-验签完整往返应成功")
        void shouldRoundtripLongData() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("签名验签长文本测试-").append(i);
            }
            String data = sb.toString();
            String signature = SignUtil.sign(data, rsaKeyPair.getPrivateKey());
            assertTrue(VerifyUtil.verify(data, signature, rsaKeyPair.getPublicKey()));
        }
    }
}
