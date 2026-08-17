package io.github.leylaragg.letool.cipher.sm;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SM4-GCM 安全契约测试。
 */
class Sm4UtilTest {

    /**
     * 验证 SM4 使用版本化认证加密封装完成往返。
     */
    @Test
    void shouldRoundTripWithAuthenticatedEnvelope() {
        String key = Sm4Util.generateKey();
        String encrypted = Sm4Util.encrypt("国密数据", key, "order-1001");

        assertTrue(encrypted.startsWith("LT1.SM4_GCM."));
        assertEquals("国密数据", Sm4Util.decrypt(encrypted, key, "order-1001"));
    }

    /**
     * 验证 SM4 密文被篡改后必须失败。
     */
    @Test
    void shouldRejectTamperedCiphertext() {
        String key = Sm4Util.generateKey();
        String encrypted = Sm4Util.encrypt("payload", key);
        int index = encrypted.lastIndexOf('.') + 1;
        char replacement = encrypted.charAt(index) == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, index) + replacement + encrypted.substring(index + 1);

        CipherException exception = assertThrows(
                CipherException.class,
                () -> Sm4Util.decrypt(tampered, key));

        assertEquals("CIPHER_005", exception.getCode());
    }

    /**
     * 验证 SM4 密钥必须严格为 128 位。
     */
    @Test
    void shouldRejectInvalidKeyLength() {
        String invalidKey = java.util.Base64.getEncoder().encodeToString(new byte[32]);

        CipherException exception = assertThrows(
                CipherException.class,
                () -> Sm4Util.encrypt("payload", invalidKey));

        assertEquals("CIPHER_002", exception.getCode());
    }
}
