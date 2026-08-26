package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.consistency.CacheReadValidation;
import io.github.leylaragg.letool.cache.consistency.CacheConsistencyMode;
import io.github.leylaragg.letool.cache.exception.CacheErrorCode;
import io.github.leylaragg.letool.cache.exception.CacheException;
import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.cache.serializer.JacksonCacheSerializer;
import io.github.leylaragg.letool.redis.RedisFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * KV 缓存一致性故障边界测试。
 */
@DisplayName("KV 缓存一致性故障边界")
class MultiLevelCacheConsistencyTest {

    @Test
    @DisplayName("严格写策略下 Redis 写入失败必须保留旧 L1 并向调用方暴露")
    void strictPutShouldExposeRedisFailureWithoutReplacingLocalValue() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> data = mock(BoundValueOperations.class);
        when(redisFacade.boundValueOps("test:critical:rule-1")).thenReturn(data);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .strongConsistency(false)
                        .writeFailurePolicy(CacheWriteFailurePolicy.FAIL_CLOSED)
                        .build(),
                redisFacade,
                new JacksonCacheSerializer()
        );
        cache.put("rule-1", "old-value");
        RuntimeException redisFailure = new RuntimeException("put failed");
        doThrow(redisFailure).when(data).set(eq("new-value"), any(Duration.class));

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.put("rule-1", "new-value"));

        assertEquals("CACHE_006", thrown.getCode());
        assertSame(redisFailure, thrown.getCause());
        assertEquals("old-value", cache.getIfPresent("rule-1"));
    }

    @Test
    @DisplayName("严格写策略下 Redis 删除失败必须保留 L1 并向调用方暴露")
    void strictEvictShouldExposeRedisFailureWithoutClearingLocalValue() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> data = mock(BoundValueOperations.class);
        when(redisFacade.boundValueOps("test:critical:rule-1")).thenReturn(data);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .strongConsistency(false)
                        .writeFailurePolicy(CacheWriteFailurePolicy.FAIL_CLOSED)
                        .build(),
                redisFacade,
                new JacksonCacheSerializer(),
                publisher,
                "node-a",
                () -> { }
        );
        cache.put("rule-1", "old-value");
        clearInvocations(publisher);
        RuntimeException redisFailure = new RuntimeException("delete failed");
        doThrow(redisFailure).when(redisFacade).delete("test:critical:rule-1");

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.evict("rule-1"));

        assertEquals("CACHE_006", thrown.getCode());
        assertSame(redisFailure, thrown.getCause());
        assertEquals("old-value", cache.getIfPresent("rule-1"));
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("兼容写策略下 Redis 删除失败仍保持原有本地降级行为")
    void bestEffortEvictShouldKeepLegacyLocalDegradationBehavior() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> data = mock(BoundValueOperations.class);
        when(redisFacade.boundValueOps("test:ordinary:rule-1")).thenReturn(data);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("ordinary")
                        .redisKeyPrefix("test:")
                        .strongConsistency(false)
                        .build(),
                redisFacade,
                new JacksonCacheSerializer()
        );
        cache.put("rule-1", "old-value");
        doThrow(new RuntimeException("delete failed"))
                .when(redisFacade).delete("test:ordinary:rule-1");

        cache.evict("rule-1");

        assertNull(cache.getIfPresent("rule-1"));
        assertTrue(cache.isL2Degraded());
    }

    @Test
    @DisplayName("evictAll 的 Redis 区域清理失败时不得广播 ALL")
    void evictAllShouldFailClosedWithoutPublishingWhenRegionCleanupFails() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        RuntimeException cleanupFailure = new RuntimeException("scan failed");
        when(redisFacade.getTemplate()).thenThrow(cleanupFailure);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .strongConsistency(false)
                        .build(),
                redisFacade,
                new JacksonCacheSerializer(),
                publisher,
                "node-a",
                () -> { }
        );

        CacheException exception = assertThrows(CacheException.class, cache::evictAll);

        assertEquals("CACHE_006", exception.getCode());
        assertSame(cleanupFailure, exception.getCause());
        assertTrue(cache.isL2Degraded());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("evictAll 的区域版本推进失败时不得广播 ALL")
    void evictAllShouldFailClosedWithoutPublishingWhenRegionVersionBumpFails() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RuntimeException bumpFailure = new RuntimeException("region version bump failed");
        when(redisFacade.getTemplate()).thenReturn(redisTemplate);
        doReturn(StringRedisSerializer.UTF_8).when(redisTemplate).getKeySerializer();
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(0L);
        when(redisFacade.increment(any(), eq(1L))).thenThrow(bumpFailure);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .strongConsistency(true)
                        .build(),
                redisFacade,
                new JacksonCacheSerializer(),
                publisher,
                "node-a",
                () -> { }
        );

        CacheException exception = assertThrows(CacheException.class, cache::evictAll);

        assertEquals("CACHE_006", exception.getCode());
        assertSame(bumpFailure, exception.getCause());
        assertTrue(cache.isL2Degraded());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("evictAll 在 L2 已降级时不得重试清理或广播 ALL")
    void evictAllShouldFailClosedWithoutRedisRetryWhenL2IsAlreadyDegraded() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        RuntimeException failure = new RuntimeException("redis unavailable");
        when(redisFacade.getTemplate()).thenThrow(failure);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .strongConsistency(false)
                        .build(),
                redisFacade,
                new JacksonCacheSerializer(),
                publisher,
                "node-a",
                () -> { }
        );
        assertThrows(CacheException.class, cache::evictAll);
        org.mockito.Mockito.clearInvocations(redisFacade, publisher);

        CacheException exception = assertThrows(CacheException.class, cache::evictAll);

        assertEquals("CACHE_006", exception.getCode());
        assertSame(failure, exception.getCause());
        verify(redisFacade, never()).getTemplate();
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("区域清理推进纪元后不得把旧 Redis 命中重新标记为新纪元")
    void redisHitShouldKeepTheRegionEpochValidatedByTheRead() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> regionOperations = mock(BoundValueOperations.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> versionOperations = mock(BoundValueOperations.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> dataOperations = mock(BoundValueOperations.class);
        AtomicInteger regionReads = new AtomicInteger();
        when(regionOperations.get()).thenAnswer(invocation ->
                regionReads.incrementAndGet() <= 2 ? 0L : 1L);
        when(versionOperations.get()).thenReturn(7L);
        when(dataOperations.get()).thenReturn("old-value").thenReturn((Object) null);
        when(redisFacade.getExpire(anyString(), eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn(60_000L);
        when(redisFacade.boundValueOps(anyString())).thenAnswer(invocation -> {
            String redisKey = invocation.getArgument(0);
            if (redisKey.endsWith("region-version")) {
                return regionOperations;
            }
            if (redisKey.endsWith(":version")) {
                return versionOperations;
            }
            return dataOperations;
        });
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("epoch-read")
                        .redisKeyPrefix("test:")
                        .readValidation(CacheReadValidation.VERSIONED),
                redisFacade,
                new JacksonCacheSerializer());

        assertEquals("old-value", cache.getIfPresent("rule:1"));
        assertNull(cache.getIfPresent("rule:1"));
        assertEquals(0L, cache.estimatedSize());
    }

    @Test
    @DisplayName("区域清理跨越单 Key 写入时不得把已删除结果放入新纪元 L1")
    void singleWriteShouldNotPopulateL1AcrossRegionEpochChange() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        AtomicLong regionEpoch = new AtomicLong();
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> regionOperations = mock(BoundValueOperations.class);
        when(regionOperations.get()).thenAnswer(invocation -> regionEpoch.get());
        when(redisFacade.boundValueOps(anyString())).thenReturn(regionOperations);
        when(redisFacade.serializeValue(any())).thenReturn("old-value".getBytes(StandardCharsets.UTF_8));
        RedisTemplate<String, Object> scriptTemplate = scriptTemplate(redisFacade);
        whenScript(scriptTemplate)
                .thenAnswer(invocation -> {
                    regionEpoch.set(1L);
                    return 7L;
                });
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("epoch-write")
                        .redisKeyPrefix("test:")
                        .readValidation(CacheReadValidation.VERSIONED),
                redisFacade,
                new JacksonCacheSerializer());

        cache.put("rule:1", "old-value");

        assertEquals(0L, cache.estimatedSize());
    }

    @Test
    @DisplayName("区域清理跨越批量写入时不得把已删除结果放入新纪元 L1")
    void batchWriteShouldNotPopulateL1AcrossRegionEpochChange() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        AtomicLong regionEpoch = new AtomicLong();
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> regionOperations = mock(BoundValueOperations.class);
        when(regionOperations.get()).thenAnswer(invocation -> regionEpoch.get());
        when(redisFacade.boundValueOps(anyString())).thenReturn(regionOperations);
        when(redisFacade.serializeValue(any())).thenReturn("old-value".getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisFacade.getTemplate()).thenReturn(redisTemplate);
        doReturn(StringRedisSerializer.UTF_8).when(redisTemplate).getKeySerializer();
        when(redisTemplate.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            regionEpoch.set(1L);
            return List.of(7L);
        });
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("epoch-batch-write")
                        .redisKeyPrefix("test:")
                        .readValidation(CacheReadValidation.VERSIONED),
                redisFacade,
                new JacksonCacheSerializer());

        cache.putAll(Map.of("rule:1", "old-value"));

        assertEquals(0L, cache.estimatedSize());
    }

    @Test
    @DisplayName("VERSIONED 模式在 Redis 降级后不得信任或重新建立 L1")
    void versionedModeShouldBypassLocalCacheWhenRedisIsDegraded() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> versionOperations = mock(BoundValueOperations.class);
        when(redisFacade.serializeValue(any())).thenReturn("old".getBytes(StandardCharsets.UTF_8));
        whenScript(redisFacade).thenReturn(1L);
        when(redisFacade.boundValueOps("test:%META%:critical:version")).thenReturn(versionOperations);
        when(versionOperations.get()).thenThrow(new IllegalStateException("redis unavailable"));

        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .readValidation(CacheReadValidation.VERSIONED),
                redisFacade,
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
        RedisFacade redisFacade = mock(RedisFacade.class);
        when(redisFacade.hasKey(any())).thenReturn(true);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .consistencyMode(CacheConsistencyMode.DURABLE)
                        .readValidation(CacheReadValidation.VERSIONED),
                redisFacade,
                new JacksonCacheSerializer());

        AtomicInteger loads = new AtomicInteger();
        assertEquals("db-1", cache.getOrLoad("u1", key -> "db-" + loads.incrementAndGet()));
        assertEquals("db-2", cache.getOrLoad("u1", key -> "db-" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("DURABLE 批量读取在围栏存在时也必须失败关闭")
    void durableBatchReadShouldBypassFencedKey() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        when(redisFacade.hasKey(any())).thenReturn(true);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("critical")
                        .redisKeyPrefix("test:")
                        .consistencyMode(CacheConsistencyMode.DURABLE)
                        .readValidation(CacheReadValidation.VERSIONED),
                redisFacade,
                new JacksonCacheSerializer());

        assertEquals(0, cache.getAllPresent(Set.of("u1")).size());
    }

    @Test
    @DisplayName("单 Key 写入和删除 Lua 都刷新版本元数据 TTL")
    void writesAndDeletesShouldRefreshVersionMetadataTtl() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        when(redisFacade.serializeValue(any()))
                .thenReturn("value".getBytes(StandardCharsets.UTF_8));
        RedisTemplate<String, Object> scriptTemplate = scriptTemplate(redisFacade);
        whenScript(scriptTemplate).thenReturn(1L, 2L);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> regionVersion =
                mock(BoundValueOperations.class);
        when(redisFacade.boundValueOps("test:%META%:metadata:region-version"))
                .thenReturn(regionVersion);
        when(regionVersion.get()).thenReturn(0L);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("metadata")
                        .redisKeyPrefix("test:")
                        .versionMetadataRetention(Duration.ofDays(7))
                        .build(),
                redisFacade,
                new JacksonCacheSerializer()
        );

        cache.put("u1", "value");
        cache.evict("u1");

        String retentionMillis = String.valueOf(Duration.ofDays(7).toMillis());
        verify(scriptTemplate).execute(
                argThat((RedisScript<?> script) ->
                        script.getScriptAsString().contains("PEXPIRE")),
                any(RedisSerializer.class), any(RedisSerializer.class), anyList(),
                any(), any(), eq(retentionMillis));
        verify(scriptTemplate).execute(
                argThat((RedisScript<?> script) ->
                        script.getScriptAsString().contains("PEXPIRE")),
                any(RedisSerializer.class), any(RedisSerializer.class), anyList(),
                eq(retentionMillis));
    }

    @Test
    @DisplayName("泛型值从 Redis 原始 Map 集合恢复为声明的 DTO 集合")
    void genericValueTypeShouldRestoreCollectionElements() throws Exception {
        RedisFacade redisFacade = mock(RedisFacade.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> data = mock(BoundValueOperations.class);
        when(redisFacade.boundValueOps("test:generic:rules"))
                .thenReturn(data);
        when(data.get()).thenReturn(List.of(Map.of("code", "R1")));
        when(redisFacade.getExpire("test:generic:rules", java.util.concurrent.TimeUnit.MILLISECONDS))
                .thenReturn(Duration.ofMinutes(5).toMillis());
        Type valueType = GenericTypes.class.getDeclaredField("rules").getGenericType();
        MultiLevelCache<String, List<RuleDto>> cache = new MultiLevelCache<>(
                CacheConfig.<String, List<RuleDto>>builder("generic")
                        .redisKeyPrefix("test:")
                        .strongConsistency(false)
                        .valueType(valueType)
                        .build(),
                redisFacade,
                new JacksonCacheSerializer()
        );

        List<RuleDto> rules = cache.getIfPresent("rules");

        assertEquals(List.of(new RuleDto("R1")), rules);
    }

    @Test
    @DisplayName("旧序列化器不支持泛型单 Key 时暴露配置错误且不降级 L2")
    void genericTypeConfigurationFailureShouldEscapeSingleReadWithoutDegradation()
            throws NoSuchFieldException {
        RedisFacade redisFacade = redisReturningRawRuleList();
        MultiLevelCache<String, List<RuleDto>> cache = legacyGenericCache(redisFacade);

        CacheException exception = assertThrows(
                CacheException.class,
                () -> cache.getIfPresent("rules")
        );

        assertEquals(CacheErrorCode.GENERIC_TYPE_UNSUPPORTED.getCode(), exception.getCode());
        assertFalse(cache.isL2Degraded());
    }

    @Test
    @DisplayName("旧序列化器不支持泛型批量读取时暴露配置错误且不降级 L2")
    void genericTypeConfigurationFailureShouldEscapeBatchReadWithoutDegradation()
            throws NoSuchFieldException {
        RedisFacade redisFacade = mock(RedisFacade.class);
        when(redisFacade.pipeline(any())).thenReturn(List.of(
                List.of(Map.of("code", "R1")),
                Duration.ofMinutes(5).toMillis()
        ));
        MultiLevelCache<String, List<RuleDto>> cache = legacyGenericCache(redisFacade);

        CacheException exception = assertThrows(
                CacheException.class,
                () -> cache.getAllPresent(Set.of("rules"))
        );

        assertEquals(CacheErrorCode.GENERIC_TYPE_UNSUPPORTED.getCode(), exception.getCode());
        assertFalse(cache.isL2Degraded());
    }

    @Test
    @DisplayName("泛型配置错误直接抛出时保留原始异常实例")
    void directGenericTypeConfigurationFailureShouldPreserveOriginalInstance()
            throws NoSuchFieldException {
        RedisFacade redisFacade = redisReturningRawRuleList();
        CacheException expected = CacheException.genericTypeUnsupported(genericRuleListType());
        MultiLevelCache<String, List<RuleDto>> cache = genericCache(
                redisFacade,
                genericTypeFailingSerializer(expected)
        );

        CacheException actual = assertThrows(
                CacheException.class,
                () -> cache.getIfPresent("rules")
        );

        assertSame(expected, actual);
        assertFalse(cache.isL2Degraded());
    }

    @Test
    @DisplayName("泛型配置错误被一层包装时保留原因链中的原始实例")
    void wrappedGenericTypeConfigurationFailureShouldPreserveOriginalCause()
            throws NoSuchFieldException {
        RedisFacade redisFacade = redisReturningRawRuleList();
        CacheException expected = CacheException.genericTypeUnsupported(genericRuleListType());
        MultiLevelCache<String, List<RuleDto>> cache = genericCache(
                redisFacade,
                genericTypeFailingSerializer(new RuntimeException(expected))
        );

        CacheException actual = assertThrows(
                CacheException.class,
                () -> cache.getIfPresent("rules")
        );

        assertSame(expected, actual);
        assertFalse(cache.isL2Degraded());
    }

    @Test
    @DisplayName("其他序列化异常仍按未命中处理")
    void nonGenericSerializerFailureShouldRemainCacheMiss() throws NoSuchFieldException {
        RedisFacade redisFacade = redisReturningRawRuleList();
        CacheException other = CacheException.loaderFailed(new IllegalStateException("loader"));
        MultiLevelCache<String, List<RuleDto>> cache = genericCache(
                redisFacade,
                genericTypeFailingSerializer(other)
        );

        assertNull(cache.getIfPresent("rules"));
        assertFalse(cache.isL2Degraded());
    }

    @Test
    @DisplayName("其他 L2 读取异常仍触发降级且不向外传播")
    void nonGenericL2FailureShouldRemainDegradedMiss() throws NoSuchFieldException {
        RedisFacade redisFacade = mock(RedisFacade.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> data = mock(BoundValueOperations.class);
        when(redisFacade.boundValueOps("test:legacy-generic:rules"))
                .thenReturn(data);
        when(data.get()).thenThrow(CacheException.loaderFailed(new IllegalStateException("redis")));
        MultiLevelCache<String, List<RuleDto>> cache = legacyGenericCache(redisFacade);

        assertNull(cache.getIfPresent("rules"));
        assertTrue(cache.isL2Degraded());
    }

    /**
     * 创建声明泛型值、但序列化 SPI 仅支持旧 Class 方法的缓存。
     *
     * @param redisFacade Redis 测试替身
     * @return 用于验证 SPI 能力不匹配边界的缓存
     * @throws NoSuchFieldException 测试泛型类型字段不存在时抛出
     */
    private static MultiLevelCache<String, List<RuleDto>> legacyGenericCache(
            RedisFacade redisFacade) throws NoSuchFieldException {
        return genericCache(redisFacade, classOnlySerializer());
    }

    /**
     * 创建声明泛型值并使用指定序列化器的缓存。
     *
     * @param redisFacade Redis 测试替身
     * @param serializer 待验证的序列化器
     * @return 泛型值缓存
     * @throws NoSuchFieldException 测试泛型类型字段不存在时抛出
     */
    private static MultiLevelCache<String, List<RuleDto>> genericCache(
            RedisFacade redisFacade,
            CacheSerializer serializer) throws NoSuchFieldException {
        return new MultiLevelCache<>(
                CacheConfig.<String, List<RuleDto>>builder("legacy-generic")
                        .redisKeyPrefix("test:")
                        .strongConsistency(false)
                        .valueType(genericRuleListType())
                        .build(),
                redisFacade,
                serializer
        );
    }

    /**
     * 获取测试 DTO 集合的参数化类型。
     *
     * @return {@code List<RuleDto>} 类型
     * @throws NoSuchFieldException 测试泛型类型字段不存在时抛出
     */
    private static Type genericRuleListType() throws NoSuchFieldException {
        return GenericTypes.class.getDeclaredField("rules").getGenericType();
    }

    /** 创建缓存脚本使用的 RedisTemplate mock。 */
    @SuppressWarnings("unchecked")
    private static RedisTemplate<String, Object> scriptTemplate(RedisFacade redisFacade) {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisFacade.getTemplate()).thenReturn(redisTemplate);
        return redisTemplate;
    }

    private static OngoingStubbing<Object> whenScript(RedisFacade redisFacade) {
        return whenScript(scriptTemplate(redisFacade));
    }

    /** 对统一 Lua 执行入口配置返回值或异常。 */
    private static OngoingStubbing<Object> whenScript(
            RedisTemplate<String, Object> redisTemplate) {
        return when(redisTemplate.execute(
                any(RedisScript.class), any(RedisSerializer.class),
                any(RedisSerializer.class), anyList(), any(Object[].class)));
    }

    /**
     * 模拟 Redis 返回尚未恢复元素类型的原始 Map 集合。
     *
     * @return 已配置原始缓存值的 Redis 测试替身
     */
    private static RedisFacade redisReturningRawRuleList() {
        RedisFacade redisFacade = mock(RedisFacade.class);
        @SuppressWarnings("unchecked")
        BoundValueOperations<String, Object> data = mock(BoundValueOperations.class);
        when(redisFacade.boundValueOps("test:legacy-generic:rules"))
                .thenReturn(data);
        when(data.get()).thenReturn(List.of(Map.of("code", "R1")));
        return redisFacade;
    }

    /**
     * 模拟升级前仅实现 Class 反序列化能力的自定义序列化器。
     *
     * @return 未覆盖 Type 方法的旧版 SPI 实现
     */
    private static CacheSerializer classOnlySerializer() {
        return new CacheSerializer() {
            @Override
            public <T> String serialize(T value) {
                return String.valueOf(value);
            }

            @Override
            public <T> T deserialize(String json, Class<T> clazz) {
                return null;
            }
        };
    }

    /**
     * 创建在 Type 反序列化时抛出指定异常的序列化器。
     *
     * @param failure 泛型反序列化时抛出的异常
     * @return 泛型反序列化失败的序列化器
     */
    private static CacheSerializer genericTypeFailingSerializer(RuntimeException failure) {
        return new CacheSerializer() {
            @Override
            public <T> String serialize(T value) {
                return String.valueOf(value);
            }

            @Override
            public <T> T deserialize(String json, Class<T> clazz) {
                return null;
            }

            @Override
            public Object deserialize(String json, Type type) {
                throw failure;
            }
        };
    }

    private static final class GenericTypes {
        private List<RuleDto> rules;
    }

    record RuleDto(String code) { }
}
