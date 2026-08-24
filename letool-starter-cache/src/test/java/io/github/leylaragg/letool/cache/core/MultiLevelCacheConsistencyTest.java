package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.consistency.CacheReadValidation;
import io.github.leylaragg.letool.cache.consistency.CacheConsistencyMode;
import io.github.leylaragg.letool.cache.serializer.JacksonCacheSerializer;
import io.github.leylaragg.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.BoundValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        when(redisUtil.executeScriptRaw(any(), anyList(), any(), any(), any())).thenReturn(1L);
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

    @Test
    @DisplayName("单 Key 写入和删除 Lua 都刷新版本元数据 TTL")
    void writesAndDeletesShouldRefreshVersionMetadataTtl() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        when(redisUtil.serializeValue(any()))
                .thenReturn("value".getBytes(StandardCharsets.UTF_8));
        when(redisUtil.executeScriptRaw(any(), anyList(), any(), any(), any()))
                .thenReturn(1L);
        when(redisUtil.executeScriptRaw(any(), anyList(), any()))
                .thenReturn(2L);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> regionVersion =
                mock(BoundValueOperations.class);
        when(redisUtil.boundValueOps("test:%META%:metadata:region-version"))
                .thenReturn(regionVersion);
        when(regionVersion.get()).thenReturn(0L);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("metadata")
                        .redisKeyPrefix("test:")
                        .versionMetadataRetention(Duration.ofDays(7))
                        .build(),
                redisUtil,
                new JacksonCacheSerializer()
        );

        cache.put("u1", "value");
        cache.evict("u1");

        String retentionMillis = String.valueOf(Duration.ofDays(7).toMillis());
        verify(redisUtil).executeScriptRaw(
                contains("PEXPIRE"), anyList(), any(), any(), eq(retentionMillis));
        verify(redisUtil).executeScriptRaw(
                contains("PEXPIRE"), anyList(), eq(retentionMillis));
    }

    @Test
    @DisplayName("泛型值从 Redis 原始 Map 集合恢复为声明的 DTO 集合")
    void genericValueTypeShouldRestoreCollectionElements() throws Exception {
        RedisUtil redisUtil = mock(RedisUtil.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> data = mock(BoundValueOperations.class);
        when(redisUtil.boundValueOps("test:generic:rules"))
                .thenReturn(data);
        when(data.get()).thenReturn(List.of(Map.of("code", "R1")));
        when(redisUtil.getExpire("test:generic:rules", java.util.concurrent.TimeUnit.MILLISECONDS))
                .thenReturn(Duration.ofMinutes(5).toMillis());
        Type valueType = GenericTypes.class.getDeclaredField("rules").getGenericType();
        MultiLevelCache<String, List<RuleDto>> cache = new MultiLevelCache<>(
                CacheConfig.<String, List<RuleDto>>builder("generic")
                        .redisKeyPrefix("test:")
                        .strongConsistency(false)
                        .valueType(valueType)
                        .build(),
                redisUtil,
                new JacksonCacheSerializer()
        );

        List<RuleDto> rules = cache.getIfPresent("rules");

        assertEquals(List.of(new RuleDto("R1")), rules);
    }

    private static final class GenericTypes {
        private List<RuleDto> rules;
    }

    record RuleDto(String code) { }
}
