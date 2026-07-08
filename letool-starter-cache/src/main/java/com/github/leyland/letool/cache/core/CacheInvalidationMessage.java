package com.github.leyland.letool.cache.core;

import java.util.ArrayList;
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
    /** 消息来源 JVM 的 instanceId，用于接收方忽略自己发出的消息。 */
    private final String sourceInstanceId;

    private CacheInvalidationMessage(String cacheName, List<String> keys, boolean all, String sourceInstanceId) {
        this.cacheName = cacheName;
        this.keys = keys == null ? Collections.emptyList() : List.copyOf(keys);
        this.all = all;
        this.sourceInstanceId = sourceInstanceId;
    }

    public static CacheInvalidationMessage keys(String cacheName, List<String> keys, String sourceInstanceId) {
        return new CacheInvalidationMessage(cacheName, keys, false, sourceInstanceId);
    }

    /**
     * 创建“清空整个缓存区域 L1”的消息。
     */
    public static CacheInvalidationMessage all(String cacheName, String sourceInstanceId) {
        return new CacheInvalidationMessage(cacheName, List.of(ALL_MARKER), true, sourceInstanceId);
    }

    public String getCacheName() {
        return cacheName;
    }

    public List<String> getKeys() {
        return keys;
    }

    public boolean isAll() {
        return all;
    }

    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    String toPayload() {
        // 为了不强依赖 JSON 序列化库，失效消息使用轻量字符串格式：source|cacheName|allFlag|keys。
        // 先对每个 key 单独转义（保护 key 内部的逗号和分隔符），再用逗号拼接，
        // 确保反序列化时能正确区分 key 分隔符和 key 内部内容。
        String keyPart;
        if (all) {
            keyPart = ALL_MARKER;
        } else {
            keyPart = keys.stream().map(CacheInvalidationMessage::escape).collect(java.util.stream.Collectors.joining(","));
        }
        return escape(sourceInstanceId) + "|" + escape(cacheName) + "|" + (all ? "1" : "0") + "|" + keyPart;
    }

    /**
     * 从 Redis pub/sub 的字符串 payload 还原失效消息。
     */
    static CacheInvalidationMessage fromPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("invalidation payload must not be blank");
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("invalid cache invalidation payload");
        }
        String source = unescape(parts[0]);
        String cache = unescape(parts[1]);
        boolean all = "1".equals(parts[2]);
        String rawKeys = unescape(parts[3]);
        if (all) {
            return all(cache, source);
        }
        List<String> keys = new ArrayList<>();
        if (!rawKeys.isBlank()) {
            // 按逗号切分后逐个 unescape，与 toPayload 的逐 key escape 对应
            for (String k : rawKeys.split(",")) {
                if (!k.isEmpty()) {
                    keys.add(unescape(k));
                }
            }
        }
        return keys(cache, keys, source);
    }

    private static String escape(String value) {
        // key 或 cacheName 里如果包含分隔符，需要转义，避免解析时被错误切分。
        return value == null ? "" : value.replace("\\", "\\\\").replace("|", "\\p").replace(",", "\\c");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaping) {
                out.append(c == 'p' ? '|' : c == 'c' ? ',' : c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else {
                out.append(c);
            }
        }
        if (escaping) {
            out.append('\\');
        }
        return out.toString();
    }
}
