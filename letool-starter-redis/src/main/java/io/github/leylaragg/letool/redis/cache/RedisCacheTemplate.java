package io.github.leylaragg.letool.redis.cache;

import io.github.leylaragg.letool.lock.core.LockRequest;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.lock.exception.LockException;
import io.github.leylaragg.letool.redis.exception.RedisOperationException;
import io.github.leylaragg.letool.tool.util.JsonUtil;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.Arrays;
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

    /** 与业务 value 序列化器隔离的空值协议标记。 */
    private static final byte[] NULL_VALUE_MARKER = {
            (byte) 0x89, 'L', 'E', 'T', 'O', 'O', 'L', 0,
            'N', 'U', 'L', 'L', 1, (byte) 0xFF
    };

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOperations;
    private final RedisSerializer<String> keySerializer;
    private final RedisSerializer<Object> valueSerializer;
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
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate, "redisTemplate must not be null");
        this.valueOperations = redisTemplate.opsForValue();
        this.keySerializer = requireSerializer(
                redisTemplate.getKeySerializer(), "Redis key serializer must not be null");
        this.valueSerializer = requireSerializer(
                redisTemplate.getValueSerializer(), "Redis value serializer must not be null");
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
     * @return 缓存值或数据源结果；空值标记命中时返回 {@code null}
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
            byte[] rawValue = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                    connection.stringCommands().get(serializeKey(key)));
            if (rawValue == null) {
                return CacheLookup.miss();
            }
            if (Arrays.equals(rawValue, NULL_VALUE_MARKER)) {
                return CacheLookup.cachedNull();
            }
            Object cached = valueSerializer.deserialize(rawValue);
            if (cached == null) {
                return CacheLookup.miss();
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
                setNullValue(key, policy.nullTtl());
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

    private void setNullValue(String key, Duration ttl) {
        try {
            redisTemplate.execute((RedisCallback<Boolean>) connection ->
                    connection.stringCommands().set(
                            serializeKey(key),
                            NULL_VALUE_MARKER,
                            Expiration.from(ttl),
                            RedisStringCommands.SetOption.UPSERT));
        } catch (RuntimeException exception) {
            throw RedisOperationException.operationFailed("set", key, exception);
        }
    }

    private byte[] serializeKey(String key) {
        return Objects.requireNonNull(
                keySerializer.serialize(key), "Redis key serializer returned null");
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisSerializer<T> requireSerializer(
            RedisSerializer<?> serializer, String message) {
        return (RedisSerializer<T>) Objects.requireNonNull(serializer, message);
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
