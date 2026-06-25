package com.github.leyland.letool.cache.exception;

/**
 * 缓存异常 —— 缓存操作过程中发生错误时抛出（如序列化失败、Redis 连接异常等）.
 */
public class CacheException extends RuntimeException {

    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
