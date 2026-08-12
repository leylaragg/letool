package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.consistency.CacheReadValidation;
import com.github.leyland.letool.cache.consistency.CacheConsistencyMode;
import com.github.leyland.letool.cache.serializer.JacksonCacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.BoundValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KV 缓存一致性故障边界测试。
 */
@DisplayName("KV 缓存一致性故障边界")
class MultiLevelCacheConsistencyTest {

    @Test
    @DisplayName("VERSIONED 模式在 Redis 降级后不得信任或重新建立 L1")
    void versionedModeShouldBypassLocalCacheWhenRedisIsDegraded() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> versionOperations = mock(BoundValueOperations.class);
        when(redisUtil.serializeValue(any())).thenReturn("old".getBytes(StandardCharsets.UTF_8));
        when(redisUtil.executeScriptRaw(any(), anyList(), any(), any())).thenReturn(1L);
        when(redisUtil.boundValueOps("test:%META%:critical:version")).thenReturn(versionOperations);
        when(versionOperations.get()).thenThrow(new IllegalStateException("redis unavailable"));

        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .readValidation(CacheReadValidation.VERSIONED),
                redisUtil,
                new JacksonCacheSerializer());
        cache.put("u1", "old");

        AtomicInteger loads = new AtomicInteger();
        assertEquals("db-1", cache.getOrLoad("u1", key -> "db-" + loads.incrementAndGet()));
        assertEquals("db-2", cache.getOrLoad("u1", key -> "db-" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("DURABLE 存在写围栏时必须绕过两级缓存且不回填")
    void durableModeShouldBypassCachesWhileFenceExists() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        when(redisUtil.hasKey(any())).thenReturn(true);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .consistencyMode(CacheConsistencyMode.DURABLE)
                        .readValidation(CacheReadValidation.VERSIONED),
                redisUtil,
                new JacksonCacheSerializer());

        AtomicInteger loads = new AtomicInteger();
        assertEquals("db-1", cache.getOrLoad("u1", key -> "db-" + loads.incrementAndGet()));
        assertEquals("db-2", cache.getOrLoad("u1", key -> "db-" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("DURABLE 批量读取在围栏存在时也必须失败关闭")
    void durableBatchReadShouldBypassFencedKey() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        when(redisUtil.hasKey(any())).thenReturn(true);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .consistencyMode(CacheConsistencyMode.DURABLE)
                        .readValidation(CacheReadValidation.VERSIONED),
                redisUtil,
                new JacksonCacheSerializer());

        assertEquals(0, cache.getAllPresent(Set.of("u1")).size());
    }
}
