package com.github.leyland.letool.cache.core;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * KV 二级缓存接口。
 *
 * <p>该接口抽象 key -> value 类型缓存，默认实现是 {@link DefaultMultiLevelCache}。
 * 设计上同时支持直接编程式调用和注解 AOP 调用。</p>
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存 value 类型
 */
public interface MultiLevelCache<K, V> {

    /**
     * 读穿模式获取缓存值。
     *
     * <p>实现会先查 L1，再查 L2；两级都未命中时调用 loader 回源，并把回源结果写回缓存。
     * 如果 loader 返回 null 且配置启用了 null 值缓存，则会写入空值哨兵，防止同一个不存在 key 高频穿透。</p>
     *
     * @param key 缓存 key
     * @param loader 数据加载器，通常查询数据库或远程服务
     * @return 缓存值，可能为 null
     */
    V getOrLoad(K key, Function<K, V> loader);

    /**
     * 读穿模式获取缓存值，并为本次 loader 写回指定 L2 TTL。
     *
     * <p>该方法主要服务注解场景，例如 {@code @MultiLevelCacheable(ttl = 60)}。
     * L1 实际 TTL 会取配置 L1 TTL 和传入 TTL 的较小值，避免本地缓存活得比 Redis 更久。</p>
     */
    default V getOrLoad(K key, Function<K, V> loader, Duration ttl) {
        return getOrLoad(key, loader);
    }

    /**
     * 只读取现有缓存，不触发 loader 回源。
     */
    default V getIfPresent(K key) {
        return getOrLoad(key, ignored -> null);
    }

    /**
     * 批量读取现有缓存，不触发 loader 回源。
     */
    default Map<K, V> getAllPresent(Set<K> keys) {
        return Collections.emptyMap();
    }

    /**
     * 使用默认 L2 TTL 写入缓存。
     */
    void put(K key, V value);

    /**
     * 使用指定 L2 TTL 写入缓存。
     */
    void put(K key, V value, Duration ttl);

    /**
     * 删除指定 key 的缓存。
     *
     * <p>完整实现会删除当前 JVM 的 L1、删除 Redis L2、推进缓存区域版本，并广播其它 JVM 清理 L1。</p>
     */
    void evict(K key);

    /**
     * 清空当前缓存区域。
     */
    default void evictAll() {
    }

    /**
     * 仅删除当前 JVM 的 L1 条目。
     *
     * <p>该方法供跨节点失效监听器调用，不删除 Redis，也不再次广播。</p>
     */
    default void evictLocal(K key) {
    }

    /**
     * 仅清空当前 JVM 的 L1 区域。
     */
    default void evictLocalAll() {
    }

    /**
     * 获取缓存统计快照。
     */
    CacheStats stats();

    /**
     * 获取当前 JVM L1 估算条目数。
     */
    default long estimatedSize() {
        return 0;
    }

    /**
     * 当前缓存是否已经因为 Redis 异常进入 L2 降级。
     */
    default boolean isL2Degraded() {
        return false;
    }

    /**
     * 尝试恢复 L2 访问。
     */
    default boolean tryRecoverL2() {
        return true;
    }

    /**
     * 获取缓存区域名称。
     */
    String getName();
}
