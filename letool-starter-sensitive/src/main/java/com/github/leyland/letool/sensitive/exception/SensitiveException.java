package com.github.leyland.letool.sensitive.exception;

/**
 * 脱敏异常 —— 脱敏处理过程中发生错误时抛出（如反射操作失败、不支持的脱敏类型等）.
 *
 * <p>继承 {@link RuntimeException}，无需强制 try-catch.</p>
 */
public class SensitiveException extends RuntimeException {

    public SensitiveException(String message) {
        super(message);
    }

    public SensitiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
