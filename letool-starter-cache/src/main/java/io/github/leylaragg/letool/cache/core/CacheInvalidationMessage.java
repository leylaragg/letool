package io.github.leylaragg.letool.cache.core;

import java.util.Collections;
import java.util.List;

/**
 * 跨 JVM 的 L1 失效消息。
 *
 * <p>当某个节点执行 put/evict/evictAll 后，Redis 中的数据已经被更新或删除，但其它 JVM 的 L1
 * 仍可能保存旧副本。该消息用于通过 Redis pub/sub 通知其它节点清理对应 L1。</p>
 *
 * <p>消息只负责“清理本地 L1”，不会让接收方删除 Redis，也不会再次广播。这样可以避免广播风暴和循环删除。</p>
 */
public final class CacheInvalidationMessage {

    /** 表示清空整个缓存区域的特殊 key 标记。 */
    private static final String ALL_MARKER = "*";

    /** 缓存区域名称，接收方用它找到对应的 KV 或 Set 缓存实例。 */
    private final String cacheName;
    /** 需要失效的业务 key 列表；all=true 时该字段只保存 ALL_MARKER。 */
    private final List<String> keys;
    /** 是否清空整个缓存区域的 L1。 */
    private final boolean all;
    /** 是否按业务 key 前缀清理 L1。 */
    private final boolean prefix;
    /** 消息来源 JVM 的 instanceId，用于接收方忽略自己发出的消息。 */
    private final String sourceInstanceId;

    private CacheInvalidationMessage(
            String cacheName, List<String> keys, boolean all, boolean prefix,
            String sourceInstanceId) {
        this.cacheName = cacheName;
        this.keys = keys == null ? Collections.emptyList() : List.copyOf(keys);
        this.all = all;
        this.prefix = prefix;
        this.sourceInstanceId = sourceInstanceId;
    }

    /**
     * 创建按业务 key 失效 L1 的消息。
     *
     * @param cacheName 缓存区域名称
     * @param keys 需要清理的业务 key 列表
     * @param sourceInstanceId 消息来源 JVM 实例标识
     * @return 不可变的缓存失效消息
     */
    public static CacheInvalidationMessage keys(String cacheName, List<String> keys, String sourceInstanceId) {
        return new CacheInvalidationMessage(cacheName, keys, false, false, sourceInstanceId);
    }

    /**
     * 创建“清空整个缓存区域 L1”的消息。
     *
     * @param cacheName 缓存区域名称
     * @param sourceInstanceId 消息来源 JVM 实例标识
     * @return 不可变的缓存失效消息
     */
    public static CacheInvalidationMessage all(String cacheName, String sourceInstanceId) {
        return new CacheInvalidationMessage(cacheName, List.of(ALL_MARKER), true, false, sourceInstanceId);
    }

    /**
     * 创建按序列化业务 key 前缀失效 L1 的消息。
     *
     * @param cacheName 缓存区域名称
     * @param serializedPrefix 稳定序列化后的业务 key 前缀
     * @param sourceInstanceId 消息来源 JVM 实例标识
     * @return 前缀失效消息
     */
    public static CacheInvalidationMessage prefix(
            String cacheName, String serializedPrefix, String sourceInstanceId) {
        if (serializedPrefix == null || serializedPrefix.isBlank()) {
            throw io.github.leylaragg.letool.cache.exception.CacheException
                    .invalidationMessageInvalid();
        }
        return new CacheInvalidationMessage(
                cacheName, List.of(serializedPrefix), false, true, sourceInstanceId);
    }

    /** @return 缓存区域名称 */
    public String getCacheName() {
        return cacheName;
    }

    /** @return 不可变的待失效业务 key 列表 */
    public List<String> getKeys() {
        return keys;
    }

    /** @return 是否清空整个缓存区域的 L1 */
    public boolean isAll() {
        return all;
    }

    /** @return 消息来源 JVM 实例标识 */
    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    /**
     * 将失效消息编码为 Redis Pub/Sub 载荷。
     *
     * @return 可安全传输分隔符的字符串载荷
     */
    String toPayload() {
        return CacheInvalidationWireCodec.encode(this);
    }

    /**
     * 从 Redis pub/sub 的字符串 payload 还原失效消息。
     *
     * @param payload Redis Pub/Sub 收到的字符串载荷
     * @return 解析后的缓存失效消息
     * @throws CacheException 载荷格式不合法时抛出
     */
    static CacheInvalidationMessage fromPayload(String payload) {
        return CacheInvalidationWireCodec.decode(payload);
    }

    /** @return 是否按业务 key 前缀清理 */
    public boolean isPrefix() {
        return prefix;
    }

    /** @return 序列化业务 key 前缀；非 PREFIX 消息返回 {@code null} */
    public String getPrefix() {
        return prefix && !keys.isEmpty() ? keys.get(0) : null;
    }
}
