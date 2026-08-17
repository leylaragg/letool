package io.github.leylaragg.letool.cipher.symmetric;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AES-GCM 安全契约测试。
 */
class AesCipherTest {

    private static String key;

    /**
     * 生成测试使用的随机 AES 密钥。
     */
    @BeforeAll
    static void setUpKey() {
        key = AesCipher.generateKey(256);
    }

    /**
     * 验证默认加密使用可识别的版本化 GCM 封装。
     */
    @Test
    void shouldRoundTripWithVersionedEnvelope() {
        String encrypted = AesCipher.encrypt("需要保护的数据", key);

        assertTrue(encrypted.startsWith("LT1.AES_GCM."));
        assertEquals("需要保护的数据", AesCipher.decrypt(encrypted, key));
    }

    /**
     * 验证相同明文每次使用不同随机数。
     */
    @Test
    void shouldUseFreshNonceForEveryEncryption() {
        String first = AesCipher.encrypt("same", key);
        String second = AesCipher.encrypt("same", key);

        assertNotEquals(first, second);
    }

    /**
     * 验证调用者附加认证数据参与完整性保护。
     */
    @Test
    void shouldAuthenticateAdditionalData() {
        String encrypted = AesCipher.encrypt("payload", key, "tenant-1001");

        assertEquals("payload", AesCipher.decrypt(encrypted, key, "tenant-1001"));
        CipherException exception = assertThrows(
                CipherException.class,
                () -> AesCipher.decrypt(encrypted, key, "tenant-1002"));
        assertEquals("CIPHER_005", exception.getCode());
    }

    /**
     * 验证密文被篡改后必须认证失败，不能降级尝试 CBC。
     */
    @Test
    void shouldRejectTamperedCiphertext() {
        String encrypted = AesCipher.encrypt("payload", key);
        String tampered = tamperCipherSegment(encrypted);

        CipherException exception = assertThrows(
                CipherException.class,
                () -> AesCipher.decrypt(tampered, key));

        assertEquals("CIPHER_005", exception.getCode());
    }

    /**
     * 验证未知封装版本在密码运算前被拒绝。
     */
    @Test
    void shouldRejectUnknownEnvelopeVersion() {
        CipherException exception = assertThrows(
                CipherException.class,
                () -> AesCipher.decrypt("LT2.AES_GCM.AAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAA", key));

        assertEquals("CIPHER_003", exception.getCode());
    }

    /**
     * 验证非法 AES 密钥返回稳定密钥错误码。
     */
    @Test
    void shouldRejectInvalidKeyLength() {
        String invalidKey = java.util.Base64.getEncoder().encodeToString(new byte[10]);

        CipherException exception = assertThrows(
                CipherException.class,
                () -> AesCipher.encrypt("payload", invalidKey));

        assertEquals("CIPHER_002", exception.getCode());
    }

    /**
     * 修改密文段中的有效 Base64URL 字符。
     *
     * @param envelope 原始密文封装
     * @return 被修改后的密文封装
     */
    private static String tamperCipherSegment(String envelope) {
        int index = envelope.lastIndexOf('.') + 1;
        char replacement = envelope.charAt(index) == 'A' ? 'B' : 'A';
        return envelope.substring(0, index) + replacement + envelope.substring(index + 1);
    }
}
