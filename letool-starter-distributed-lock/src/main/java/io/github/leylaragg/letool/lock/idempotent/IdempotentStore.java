package io.github.leylaragg.letool.lock.idempotent;

import java.time.Duration;

/**
 * 幂等占位存储的后端无关契约。
 *
 * <p>实现必须保证“key 不存在时写入并设置 TTL”是一个原子操作。</p>
 */
public interface IdempotentStore {

    /**
     * 首次请求时写入占位标记。
     *
     * @param key 已完成业务命名的幂等 key
     * @param ttl 标记存活时间，必须大于零
     * @return {@code true} 表示本次成功占位；{@code false} 表示请求重复
     */
    boolean putIfAbsent(String key, Duration ttl);

    /**
     * 移除失败业务留下的占位，使后续请求可以重试。
     *
     * @param key 幂等 key
     */
    void remove(String key);
}
