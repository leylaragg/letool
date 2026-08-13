package com.github.leyland.letool.cache.consistency;

import com.github.leyland.letool.tool.util.DigestUtil;

import java.util.Objects;

/**
 * Redis Cluster 中单个缓存业务 Key 的稳定键身份。
 *
 * <p>数据、版本、围栏和幂等标记共享同一个 Hash Tag，因此涉及这些 Key 的 Lua 脚本
 * 在 Redis Cluster 中不会产生 CROSSSLOT。不同业务 Key 使用不同摘要，避免整个缓存区域
 * 被固定到同一个 Slot。</p>
 *
 * @param dataKey 业务数据 Key
 * @param versionKey 单 Key 版本 Key
 * @param fenceKey 写事务围栏 Key
 * @param processedKey 最近完成事件的幂等标记 Key
 */
public record CacheKeyIdentity(
        String dataKey,
        String versionKey,
        String fenceKey,
        String processedKey) {

    /**
     * 根据缓存区域和业务 Key 创建同槽键身份。
     *
     * @param redisPrefix Redis 全局前缀
     * @param cacheName 缓存区域名称
     * @param serializedKey 已序列化业务 Key
     * @return 四个同槽 Redis Key
     */
    public static CacheKeyIdentity of(String redisPrefix, String cacheName, String serializedKey) {
        Objects.requireNonNull(redisPrefix, "Redis Key 前缀不能为空");
        String encodedName = encodeSegment(Objects.requireNonNull(cacheName, "缓存名称不能为空"));
        String businessKey = Objects.requireNonNull(serializedKey, "业务 Key 不能为空");
        String tag = "{" + digest(businessKey) + "}";
        String metadataBase = redisPrefix + "%META%:" + encodedName + ":" + tag;
        return new CacheKeyIdentity(
                redisPrefix + encodedName + ":" + tag + ":" + businessKey,
                metadataBase + ":version",
                metadataBase + ":fence",
                metadataBase + ":processed");
    }

    private static String encodeSegment(String value) {
        return value.replace("%", "%25").replace(":", "%3A");
    }

    private static String digest(String value) {
        return DigestUtil.sha256(value).substring(0, 24);
    }
}
