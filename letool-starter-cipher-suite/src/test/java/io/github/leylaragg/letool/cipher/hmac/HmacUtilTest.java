package io.github.leylaragg.letool.cipher.hmac;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import io.github.leylaragg.letool.cipher.key.KeyGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HMAC 密钥和消息认证关键契约测试。
 */
class HmacUtilTest {

    /**
     * 验证 RFC 4231 中超长密钥的 HMAC-SHA256 公开向量。
     */
    @Test
    void shouldMatchRfc4231Sha256Vector() {
        byte[] key = new byte[131];
        Arrays.fill(key, (byte) 0xaa);

        String result = HmacUtil.hmacSha256(
                "Test Using Larger Than Block-Size Key - Hash Key First",
                key);

        assertEquals("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54", result);
    }

    /**
     * 验证生成的 Base64 密钥可直接用于字符串 API 和常量时间校验。
     */
    @Test
    void shouldUseGeneratedBase64KeyDirectly() {
        String key = KeyGenerator.generateHmacKey();
        String mac = HmacUtil.hmacSha256("payload", key);

        assertTrue(HmacUtil.verifySha256("payload", mac, key));
        assertFalse(HmacUtil.verifySha256("changed", mac, key));
    }

    /**
     * 验证字符串密钥按 Base64 原始字节解释，而不是按 Base64 文本解释。
     */
    @Test
    void shouldDecodeStringKeyAsBase64() {
        byte[] rawKey = new byte[32];
        Arrays.fill(rawKey, (byte) 0x5a);
        String base64Key = Base64.getEncoder().encodeToString(rawKey);

        assertEquals(
                HmacUtil.hmacSha256("payload", rawKey),
                HmacUtil.hmacSha256("payload", base64Key));
    }

    /**
     * 验证过短 HMAC 密钥被生产 API 拒绝。
     */
    @Test
    void shouldRejectShortKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        CipherException exception = assertThrows(
                CipherException.class,
                () -> HmacUtil.hmacSha256("payload", shortKey));

        assertEquals("CIPHER_002", exception.getCode());
    }

    /**
     * 验证 SHA-256 消息认证码必须解码为固定的 32 字节。
     */
    @Test
    void shouldRejectUnexpectedSha256MacLength() {
        String key = KeyGenerator.generateHmacKey();
        String oversizedHex = "00".repeat(33);
        String oversizedBase64 = Base64.getEncoder().encodeToString(new byte[33]);

        CipherException hexException = assertThrows(
                CipherException.class,
                () -> HmacUtil.verifySha256("payload", oversizedHex, key));
        CipherException base64Exception = assertThrows(
                CipherException.class,
                () -> HmacUtil.verifySha256Base64("payload", oversizedBase64, key));

        assertEquals("CIPHER_001", hexException.getCode());
        assertEquals("CIPHER_001", base64Exception.getCode());
    }
}
