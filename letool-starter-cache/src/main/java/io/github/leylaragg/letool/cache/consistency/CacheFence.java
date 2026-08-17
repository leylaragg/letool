package io.github.leylaragg.letool.cache.consistency;

import java.time.Instant;

/**
 * 已成功建立的缓存写入围栏。
 *
 * @param cacheName 缓存区域名称
 * @param serializedKey 已序列化的业务 Key
 * @param eventId 对应的持久化失效事件 ID
 * @param token 本次写事务唯一令牌
 * @param createdAt 围栏创建时间
 */
public record CacheFence(
        String cacheName,
        String serializedKey,
        String eventId,
        String token,
        Instant createdAt) {
}
