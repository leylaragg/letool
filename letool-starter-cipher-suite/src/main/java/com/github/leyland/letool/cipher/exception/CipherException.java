package com.github.leyland.letool.cipher.exception;

/**
 * 加密异常 —— 加解密操作失败时抛出（如密钥格式错误、算法不支持、填充异常等）.
 */
public class CipherException extends RuntimeException {

    public CipherException(String message) {
        super(message);
    }

    public CipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
