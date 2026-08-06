package com.github.leyland.letool.cipher.util;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 加密统一门面关键用法测试。
 */
class CipherUtilTest {

    /**
     * 验证门面暴露 AES-GCM 附加认证数据能力。
     */
    @Test
    void shouldExposeAesAdditionalAuthenticatedData() {
        String key = CipherUtil.generateAesKey(256);
        String encrypted = CipherUtil.aesEncrypt("payload", key, "tenant-1001");

        assertEquals("payload", CipherUtil.aesDecrypt(encrypted, key, "tenant-1001"));
    }

    /**
     * 验证门面提供可直接使用的 HMAC 生成与校验流程。
     */
    @Test
    void shouldExposeHmacGenerationAndVerification() {
        String key = CipherUtil.generateHmacKey();
        String mac = CipherUtil.hmacSha256("payload", key);

        assertTrue(CipherUtil.verifyHmacSha256("payload", mac, key));
        assertFalse(CipherUtil.verifyHmacSha256("changed", mac, key));
    }

    /**
     * 验证门面默认提供 RSA-OAEP 加密与 RSA-PSS 签名。
     */
    @Test
    void shouldExposeSafeRsaDefaults() {
        RsaCipher.RsaKeyPair keyPair = CipherUtil.generateRsaKeyPair(2048);
        String encrypted = CipherUtil.rsaEncrypt("session-key", keyPair.getPublicKey());
        String signature = CipherUtil.sign("payload", keyPair.getPrivateKey());

        assertEquals("session-key", CipherUtil.rsaDecrypt(encrypted, keyPair.getPrivateKey()));
        assertTrue(CipherUtil.verify("payload", signature, keyPair.getPublicKey()));
    }
}
