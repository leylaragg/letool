package io.github.leylaragg.letool.cipher.exception;

import io.github.leylaragg.letool.exception.core.SystemException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 加密模块统一异常关键契约测试。
 */
class CipherExceptionTest {

    /**
     * 验证参数异常使用稳定错误码且接入统一系统异常体系。
     */
    @Test
    void shouldExposeStableCodeForInvalidParameter() {
        CipherException exception = CipherException.invalidParameter("AES 密钥不能为空");

        assertInstanceOf(SystemException.class, exception);
        assertEquals("CIPHER_001", exception.getCode());
        assertEquals("加密参数无效：AES 密钥不能为空", exception.getFallbackMessage());
    }

    /**
     * 验证执行异常保留底层原因但不要求传入敏感数据。
     */
    @Test
    void shouldRetainCauseForEncryptionFailure() {
        IllegalStateException cause = new IllegalStateException("provider failed");

        CipherException exception = CipherException.encryptionFailed("AES-GCM", cause);

        assertEquals("CIPHER_004", exception.getCode());
        assertSame(cause, exception.getCause());
        assertEquals("加密执行失败：AES-GCM", exception.getFallbackMessage());
    }
}
