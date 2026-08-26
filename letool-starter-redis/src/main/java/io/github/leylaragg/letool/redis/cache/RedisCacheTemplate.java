package io.github.leylaragg.letool.redis.cache;

import io.github.leylaragg.letool.lock.core.LockRequest;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.lock.exception.LockException;
import io.github.leylaragg.letool.redis.exception.RedisOperationException;
import io.github.leylaragg.letool.tool.util.JsonUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 带分布式互斥、双重检查和空值保护的单值缓存回源模板。
 *
 * <p>Redis 故障和重建锁超时默认快速失败，不会静默绕过互斥访问数据源，避免把并发压力
 * 直接转移到数据库。数据源回调异常保持原样传播。</p>
 */
public final class RedisCacheTemplate {

    private final ValueOperations<String, Object> valueOperations;
    private final LockTemplate lockTemplate;
    private final String lockKeyPrefix;

    /**
     * @param redisTemplate 对象 Redis 模板
     * @param lockTemplate 分布式锁执行模板
     * @param lockKeyPrefix 缓存重建锁的业务 key 前缀
     */
    public RedisCacheTemplate(
            RedisTemplate<String, Object> redisTemplate,
            LockTemplate lockTemplate,
            String lockKeyPrefix) {
        this.valueOperations = Objects.requireNonNull(
                redisTemplate, "redisTemplate must not be null").opsForValue();
        this.lockTemplate = Objects.requireNonNull(lockTemplate, "lockTemplate must not be null");
        if (lockKeyPrefix == null || lockKeyPrefix.isBlank()) {
            throw new IllegalArgumentException("缓存重建锁 key 前缀不能为空");
        }
        this.lockKeyPrefix = lockKeyPrefix;
    }

    /**
     * 优先读取缓存，未命中时在分布式锁内双检并调用数据源。
     *
     * @param key 缓存 key
     * @param type 期望的业务值类型
     * @param policy 本次回源和写入策略
     * @param loader 数据源回调，仅在锁内二次未命中时调用
     * @param <T> 业务值类型
     * @return 缓存值或数据源结果；空值哨兵命中时返回 {@code null}
     */
    public <T> T getOrLoad(
            String key,
            Class<T> type,
            RedisCachePolicy<T> policy,
            Supplier<T> loader) {
        validateArguments(key, type, policy, loader);
        CacheLookup<T> first = lookup(key, type);
        if (first.resolved()) {
            return first.value();
        }

        LockRequest request = LockRequest.watchdog(lockKeyPrefix + key, policy.lockWait());
        try {
            return lockTemplate.execute(request, () -> rebuild(key, type, policy, loader));
        } catch (LockException lockFailure) {
            CacheLookup<T> last = lookup(key, type);
            if (last.resolved()) {
                return last.value();
            }
            throw RedisOperationException.cacheRebuildTimeout(key, lockFailure);
        }
    }

    private <T> T rebuild(
            String key,
            Class<T> type,
            RedisCachePolicy<T> policy,
            Supplier<T> loader) {
        CacheLookup<T> second = lookup(key, type);
        if (second.resolved()) {
            return second.value();
        }
        T loaded = loader.get();
        writeLoadedValue(key, loaded, policy);
        return loaded;
    }

    private <T> CacheLookup<T> lookup(String key, Class<T> type) {
        try {
            Object cached = valueOperations.get(key);
            if (cached == null) {
                return CacheLookup.miss();
            }
            if (cached == RedisNullValue.INSTANCE) {
                return CacheLookup.cachedNull();
            }
            T converted = type.isInstance(cached)
                    ? type.cast(cached)
                    : JsonUtil.convert(cached, type);
            return CacheLookup.hit(converted);
        } catch (RuntimeException exception) {
            throw RedisOperationException.operationFailed("get", key, exception);
        }
    }

    private <T> void writeLoadedValue(
            String key, T loaded, RedisCachePolicy<T> policy) {
        if (loaded == null) {
            if (policy.cacheNull()) {
                set(key, RedisNullValue.INSTANCE, policy.nullTtl());
            }
            return;
        }
        if (policy.cacheable().test(loaded)) {
            set(key, loaded, jitteredTtl(policy));
        }
    }

    private void set(String key, Object value, Duration ttl) {
        try {
            valueOperations.set(key, value, ttl);
        } catch (RuntimeException exception) {
            throw RedisOperationException.operationFailed("set", key, exception);
        }
    }

    private static Duration jitteredTtl(RedisCachePolicy<?> policy) {
        long jitterMillis = policy.ttlJitter().toMillis();
        if (jitterMillis == 0) {
            return policy.ttl();
        }
        long addedMillis = jitterMillis == Long.MAX_VALUE
                ? ThreadLocalRandom.current().nextLong(jitterMillis)
                : ThreadLocalRandom.current().nextLong(jitterMillis + 1);
        return policy.ttl().plusMillis(addedMillis);
    }

    private static <T> void validateArguments(
            String key,
            Class<T> type,
            RedisCachePolicy<T> policy,
            Supplier<T> loader) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("缓存 key 不能为空");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(loader, "loader must not be null");
    }

    /** 缓存读取的封闭三态：真实值、已缓存空值或未命中。 */
    private record CacheLookup<T>(boolean resolved, T value) {

        private static <T> CacheLookup<T> hit(T value) {
            return new CacheLookup<>(true, value);
        }

        private static <T> CacheLookup<T> cachedNull() {
            return new CacheLookup<>(true, null);
        }

        private static <T> CacheLookup<T> miss() {
            return new CacheLookup<>(false, null);
        }
    }
}
