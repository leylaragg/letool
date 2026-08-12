package com.github.leyland.letool.cache.consistency;

import java.time.Instant;
import java.util.Objects;

/**
 * 可持久化、可重放的单 Key 缓存失效事件。
 *
 * @param eventId 全局唯一事件 ID
 * @param cacheName 缓存区域名称
 * @param serializedKey 已序列化业务 Key
 * @param fenceToken Redis 写入围栏令牌
 * @param status 事件状态
 * @param attemptCount 已失败次数
 * @param nextAttemptAt 下一次允许处理的时间
 * @param createdAt 创建时间
 * @param leaseOwner 当前处理租约持有者；未领取时为 {@code null}
 */
public record CacheInvalidationEvent(
        String eventId,
        String cacheName,
        String serializedKey,
        String fenceToken,
        CacheInvalidationEventStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant createdAt,
        String leaseOwner) {

    public CacheInvalidationEvent {
        Objects.requireNonNull(eventId, "事件 ID 不能为空");
        Objects.requireNonNull(cacheName, "缓存名称不能为空");
        Objects.requireNonNull(serializedKey, "业务 Key 不能为空");
        Objects.requireNonNull(fenceToken, "围栏令牌不能为空");
        Objects.requireNonNull(status, "事件状态不能为空");
        Objects.requireNonNull(nextAttemptAt, "下次处理时间不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("重试次数不能小于零");
        }
    }

    /**
     * 兼容未携带租约令牌的旧构造方式。
     */
    public CacheInvalidationEvent(
            String eventId,
            String cacheName,
            String serializedKey,
            String fenceToken,
            CacheInvalidationEventStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            Instant createdAt) {
        this(eventId, cacheName, serializedKey, fenceToken, status,
                attemptCount, nextAttemptAt, createdAt, null);
    }

    /**
     * 创建一条立即可处理的新事件。
     *
     * @param eventId 全局唯一事件 ID
     * @param cacheName 缓存区域名称
     * @param serializedKey 已序列化业务 Key
     * @param fenceToken Redis 围栏令牌
     * @param createdAt 创建时间
     * @return 待处理事件
     */
    public static CacheInvalidationEvent pending(
            String eventId,
            String cacheName,
            String serializedKey,
            String fenceToken,
            Instant createdAt) {
        return new CacheInvalidationEvent(
                eventId, cacheName, serializedKey, fenceToken,
                CacheInvalidationEventStatus.PENDING, 0, createdAt, createdAt, null);
    }
}
