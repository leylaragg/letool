package com.github.leyland.letool.cache.consistency;

import com.github.leyland.letool.tool.redis.RedisUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 使用 Redis Lua 管理单业务 Key 写入围栏。
 *
 * <p>建立围栏时会原子删除旧数据并推进单 Key 版本，然后为围栏设置有限 TTL。
 * 即使应用在围栏建立后立即崩溃，旧缓存也已经失效；围栏超时后读取只会从数据库重建，
 * 不会重新暴露事务前的缓存值。</p>
 */
public class RedisCacheFenceStore {

    private static final String ACQUIRE_SCRIPT = """
            local current = redis.call('GET', KEYS[3])
            if current then
              if current == ARGV[1] .. ':' .. ARGV[2] then return 1 end
              return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('INCR', KEYS[2])
            redis.call('SET', KEYS[3], ARGV[1] .. ':' .. ARGV[2], 'PX', ARGV[3])
            return 1
            """;

    private static final String STATE_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then return 1 end
            return 0
            """;

    private static final String COMPLETE_SCRIPT = """
            if redis.call('GET', KEYS[4]) == ARGV[2] then return 2 end
            local current = redis.call('GET', KEYS[3])
            if current and current ~= ARGV[1] .. ':' .. ARGV[2] then return 0 end
            redis.call('DEL', KEYS[1])
            redis.call('INCR', KEYS[2])
            redis.call('SET', KEYS[4], ARGV[2], 'PX', ARGV[3])
            redis.call('DEL', KEYS[3])
            return 1
            """;

    /** Redis 操作入口。 */
    private final RedisUtil redisUtil;
    /** 全局 Redis Key 前缀。 */
    private final String redisPrefix;
    /** 围栏最大存活时间。 */
    private final Duration staleAfter;

    /**
     * 创建 Redis 围栏存储。
     *
     * @param redisUtil Redis 操作入口
     * @param redisPrefix Redis 全局 Key 前缀
     * @param staleAfter 围栏最大存活时间
     */
    public RedisCacheFenceStore(RedisUtil redisUtil, String redisPrefix, Duration staleAfter) {
        this.redisUtil = Objects.requireNonNull(redisUtil, "Redis 操作入口不能为空");
        this.redisPrefix = Objects.requireNonNull(redisPrefix, "Redis Key 前缀不能为空");
        this.staleAfter = Objects.requireNonNull(staleAfter, "围栏超时时间不能为空");
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("围栏超时时间必须大于零");
        }
    }

    /**
     * 在数据库 SQL 执行前建立写入围栏并使旧缓存失效。
     *
     * @param cacheName 缓存区域名称
     * @param serializedKey 已序列化业务 Key
     * @param eventId 持久化事件 ID
     * @return 已建立围栏
     */
    public CacheFence acquire(String cacheName, String serializedKey, String eventId) {
        CacheKeyIdentity identity = CacheKeyIdentity.of(redisPrefix, cacheName, serializedKey);
        String token = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        try {
            Long acquired = toLong(redisUtil.executeScriptRaw(
                    ACQUIRE_SCRIPT,
                    List.of(identity.dataKey(), identity.versionKey(), identity.fenceKey()),
                    token, eventId, String.valueOf(staleAfter.toMillis())));
            if (!Long.valueOf(1L).equals(acquired)) {
                throw new CacheFenceUnavailableException();
            }
            return new CacheFence(cacheName, serializedKey, eventId, token, createdAt);
        } catch (CacheFenceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CacheFenceUnavailableException(exception);
        }
    }

    /**
     * 查询业务 Key 当前围栏状态。
     *
     * @param cacheName 缓存区域名称
     * @param serializedKey 已序列化业务 Key
     * @return 明确无围栏、有围栏或无法确认
     */
    public CacheFenceState state(String cacheName, String serializedKey) {
        CacheKeyIdentity identity = CacheKeyIdentity.of(redisPrefix, cacheName, serializedKey);
        try {
            Long fenced = toLong(redisUtil.executeScriptRaw(STATE_SCRIPT, List.of(identity.fenceKey())));
            return Long.valueOf(1L).equals(fenced) ? CacheFenceState.FENCED : CacheFenceState.CLEAR;
        } catch (Exception exception) {
            return CacheFenceState.UNKNOWN;
        }
    }

    /**
     * 按 token 幂等完成缓存失效并解除围栏。
     *
     * @param fence 待完成围栏
     * @return 完成、已处理或已经被后续事务覆盖
     */
    public CacheFenceCompletion complete(CacheFence fence) {
        Objects.requireNonNull(fence, "缓存围栏不能为空");
        CacheKeyIdentity identity = CacheKeyIdentity.of(
                redisPrefix, fence.cacheName(), fence.serializedKey());
        try {
            Long result = toLong(redisUtil.executeScriptRaw(
                    COMPLETE_SCRIPT,
                    List.of(identity.dataKey(), identity.versionKey(),
                            identity.fenceKey(), identity.processedKey()),
                    fence.token(), fence.eventId(), String.valueOf(staleAfter.multipliedBy(10).toMillis())));
            if (Long.valueOf(1L).equals(result)) {
                return CacheFenceCompletion.COMPLETED;
            }
            if (Long.valueOf(2L).equals(result)) {
                return CacheFenceCompletion.ALREADY_COMPLETED;
            }
            return CacheFenceCompletion.SUPERSEDED;
        } catch (Exception exception) {
            throw new CacheFenceUnavailableException(exception);
        }
    }

    /**
     * 获取围栏最大存活时间。
     *
     * @return 非空正时长
     */
    public Duration staleAfter() {
        return staleAfter;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }
}
