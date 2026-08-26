package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.exception.CacheException;

/** 缓存写失败策略的包内公共判断，避免五种缓存产生不同异常契约。 */
final class CacheWriteFailureSupport {

    private CacheWriteFailureSupport() {
    }

    /**
     * 严格策略下暴露 Redis 根因，兼容策略保持原有控制流。
     *
     * @param policy 当前缓存写失败策略
     * @param cause Redis 失败或结果不可确认的原因
     * @throws CacheException 严格策略下统一抛出 CACHE_006
     */
    static void throwIfStrict(CacheWriteFailurePolicy policy, Throwable cause) {
        if (policy == CacheWriteFailurePolicy.FAIL_CLOSED) {
            throw CacheException.l2Unavailable(cause);
        }
    }

    /**
     * 为没有返回确认结果的 Redis 命令创建不含业务数据的诊断原因。
     *
     * @param operation Redis 命令或操作名称
     * @return 可安全保留在异常链中的原因
     */
    static IllegalStateException unconfirmed(String operation) {
        return new IllegalStateException("Redis " + operation + " 未返回可确认结果");
    }
}
