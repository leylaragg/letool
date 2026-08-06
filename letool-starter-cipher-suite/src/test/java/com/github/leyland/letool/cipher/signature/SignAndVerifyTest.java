package com.github.leyland.letool.cipher.signature;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import com.github.leyland.letool.cipher.exception.CipherException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSA-PSS 数字签名关键契约测试。
 */
class SignAndVerifyTest {

    private static final PSSParameterSpec PSS_PARAMETERS = new PSSParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            32,
            1);

    private static RsaCipher.RsaKeyPair keyPair;

    /**
     * 生成签名测试使用的密钥对。
     */
    @BeforeAll
    static void setUpKeyPair() {
        keyPair = RsaCipher.generateKeyPair(2048);
    }

    /**
     * 验证默认签名使用 RSA-PSS-SHA256 固定参数。
     *
     * @throws Exception JCA 验证初始化失败时由测试框架报告
     */
    @Test
    void shouldProduceStandardRsaPssSignature() throws Exception {
        String signatureValue = SignUtil.sign("payload", keyPair.getPrivateKey());
        Signature verifier = Signature.getInstance("RSASSA-PSS");
        verifier.setParameter(PSS_PARAMETERS);
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(keyPair.getPublicKey()))));
        verifier.update("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(verifier.verify(Base64.getDecoder().decode(signatureValue)));
    }

    /**
     * 验证门面验签接受原始数据并拒绝被修改的数据。
     */
    @Test
    void shouldRejectChangedData() {
        String signature = SignUtil.sign("payload", keyPair.getPrivateKey());

        assertTrue(VerifyUtil.verify("payload", signature, keyPair.getPublicKey()));
        assertFalse(VerifyUtil.verify("changed", signature, keyPair.getPublicKey()));
    }

    /**
     * 验证验签入口在密码运算前拒绝不符合 RSA 模数长度的签名。
     */
    @Test
    void shouldRejectUnexpectedSignatureLength() {
        String oversizedSignature = Base64.getEncoder().encodeToString(new byte[257]);

        CipherException exception = assertThrows(
                CipherException.class,
                () -> VerifyUtil.verify("payload", oversizedSignature, keyPair.getPublicKey()));

        assertEquals("CIPHER_001", exception.getCode());
    }
}
