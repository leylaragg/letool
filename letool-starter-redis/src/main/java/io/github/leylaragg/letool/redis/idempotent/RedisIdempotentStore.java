package io.github.leylaragg.letool.redis.idempotent;

import io.github.leylaragg.letool.lock.idempotent.IdempotentStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * 使用 Redis {@code SET NX} 与 TTL 实现的幂等占位存储。
 */
public final class RedisIdempotentStore implements IdempotentStore {

    private static final String MARKER = "DONE";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    /**
     * @param redisTemplate 字符串 Redis 模板
     * @param keyPrefix 幂等 key 前缀
     */
    public RedisIdempotentStore(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("幂等 key 前缀不能为空");
        }
        this.keyPrefix = keyPrefix;
    }

    /**
     * 原子写入占位与 TTL；Redis 返回空时按失败处理。
     */
    @Override
    public boolean putIfAbsent(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("幂等 key 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("幂等 TTL 必须大于零");
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(keyPrefix + key, MARKER, ttl));
    }

    /** @param key 需要撤销占位的业务幂等 key */
    @Override
    public void remove(String key) {
        redisTemplate.delete(keyPrefix + key);
    }
}
