package com.github.leyland.letool.tool.random;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 随机数范围、字符串长度或字符表不符合契约时抛出的统一异常。
 */
public final class RandomOperationException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建随机工具统一异常。
     *
     * @param errorCode 随机工具稳定错误码
     * @param parameterName 安全的参数名称
     * @param cause 底层参数异常
     */
    private RandomOperationException(
            RandomErrorCode errorCode,
            String parameterName,
            Throwable cause) {
        super(errorCode, new Object[]{safe(parameterName)}, null, cause);
    }

    /**
     * 创建随机数范围无效异常。
     *
     * @param parameterName 安全的范围参数名称
     * @return 范围异常
     */
    public static RandomOperationException invalidRange(String parameterName) {
        String safeName = safe(parameterName);
        return new RandomOperationException(
                RandomErrorCode.INVALID_RANGE,
                safeName,
                new IllegalArgumentException("Invalid random range: " + safeName)
        );
    }

    /**
     * 创建随机字符串长度无效异常。
     *
     * @param parameterName 安全的长度参数名称
     * @return 长度异常
     */
    public static RandomOperationException invalidLength(String parameterName) {
        String safeName = safe(parameterName);
        return new RandomOperationException(
                RandomErrorCode.INVALID_LENGTH,
                safeName,
                new IllegalArgumentException("Invalid random length: " + safeName)
        );
    }

    /**
     * 创建随机字符表无效异常。
     *
     * @param parameterName 安全的字符表参数名称
     * @return 字符表异常
     */
    public static RandomOperationException invalidAlphabet(String parameterName) {
        String safeName = safe(parameterName);
        return new RandomOperationException(
                RandomErrorCode.INVALID_ALPHABET,
                safeName,
                new IllegalArgumentException("Invalid random alphabet: " + safeName)
        );
    }

    /**
     * 规范化公开消息中的安全参数名称。
     *
     * @param value 待规范化参数名称
     * @return 非空且非空白的安全参数名称
     */
    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
