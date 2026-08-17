package io.github.leylaragg.letool.cipher.support;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 密文封装资源边界测试。
 */
class CipherEnvelopeTest {

    /**
     * 验证封装在 Base64URL 解码前拒绝超过调用方上限的密文段。
     */
    @Test
    void shouldRejectOversizedCipherSegmentBeforeDecoding() {
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]);
        String cipherText = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[17]);
        String envelope = "LT1.AES_GCM." + nonce + "." + cipherText;

        CipherException exception = assertThrows(
                CipherException.class,
                () -> CipherEnvelope.parse(envelope, "AES_GCM", 12, 16, 16));

        assertEquals("CIPHER_003", exception.getCode());
    }
}
