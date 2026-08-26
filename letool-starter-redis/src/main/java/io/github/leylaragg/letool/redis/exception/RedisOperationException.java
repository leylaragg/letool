package io.github.leylaragg.letool.redis.exception;

/**
 * Redis 基础设施或缓存重建保护失败时抛出的统一异常。
 */
public class RedisOperationException extends RuntimeException {

    /**
     * 创建保留底层原因的 Redis 操作异常。
     *
     * @param message 可定位操作和 key 的异常消息
     * @param cause 原始异常
     */
    public RedisOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param key 未能完成重建的缓存 key
     * @param cause 锁获取异常
     * @return 缓存重建锁超时异常
     */
    public static RedisOperationException cacheRebuildTimeout(String key, Throwable cause) {
        return new RedisOperationException("缓存重建锁等待超时，key=" + key, cause);
    }

    /**
     * @param operation Redis 操作名称
     * @param key Redis key
     * @param cause 底层 Redis 异常
     * @return 带操作上下文的 Redis 异常
     */
    public static RedisOperationException operationFailed(
            String operation, String key, Throwable cause) {
        return new RedisOperationException(
                "Redis 操作失败，operation=" + operation + ", key=" + key, cause);
    }
}
