package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** KV 缓存批量读写契约测试。 */
@DisplayName("MultiLevelCache 批量读写测试")
class MultiLevelCacheBatchTest {

    @Test
    @DisplayName("putAll 保留 null 哨兵语义并忽略 null key")
    void putAllShouldPreserveNullSentinelContract() {
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("batch-local").build(),
                null,
                mock(CacheSerializer.class)
        );
        Map<String, String> entries = new HashMap<>();
        entries.put("a", "value-a");
        entries.put("missing", null);
        entries.put(null, "ignored");

        cache.putAll(entries);
        Map<String, String> result = cache.getAllPresent(
                new LinkedHashSet<>(List.of("a", "missing", "absent"))
        );

        assertEquals("value-a", result.get("a"));
        assertTrue(result.containsKey("missing"));
        assertNull(result.get("missing"));
        assertFalse(result.containsKey("absent"));
        assertEquals(1, cache.stats().getBatchWriteCount());
        assertEquals(5, cache.stats().getBatchRequestedKeyCount());
    }

    @Test
    @DisplayName("600 个冷 Key 按 256 分块只产生 3 次 Redis pipeline")
    @SuppressWarnings("unchecked")
    void getAllPresentShouldUseBoundedRedisBatches() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        RedisOperations<String, Object> operations = mock(RedisOperations.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(operations.opsForValue()).thenReturn(valueOperations);
        List<List<Object>> pipelineResults = List.of(
                repeatedReadResults(256),
                repeatedReadResults(256),
                repeatedReadResults(88)
        );
        java.util.concurrent.atomic.AtomicInteger invocation =
                new java.util.concurrent.atomic.AtomicInteger();
        when(redisUtil.pipeline(any())).thenAnswer(answer -> {
            Consumer<RedisOperations<String, Object>> callback = answer.getArgument(0);
            callback.accept(operations);
            return pipelineResults.get(invocation.getAndIncrement());
        });
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("batch-remote")
                        .l1Enabled(false)
                        .strongConsistency(false)
                        .redisBatchSize(256)
                        .build(),
                redisUtil,
                mock(CacheSerializer.class)
        );
        Set<String> keys = new LinkedHashSet<>();
        for (int index = 0; index < 600; index++) {
            keys.add("key-" + index);
        }

        Map<String, String> result = cache.getAllPresent(keys);

        assertEquals(600, result.size());
        verify(redisUtil, times(3)).pipeline(any());
        assertEquals(1, cache.stats().getBatchReadCount());
        assertEquals(600, cache.stats().getBatchHitKeyCount());
        assertEquals(3, cache.stats().getRedisBatchCount());
    }

    @Test
    @DisplayName("putAll 使用 pipeline 分块写入且不会逐项发布失效")
    @SuppressWarnings("unchecked")
    void putAllShouldPipelineWritesAndPublishOneInvalidation() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        RedisOperations<String, Object> operations = mock(RedisOperations.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(operations.opsForValue()).thenReturn(valueOperations);
        when(redisUtil.pipeline(any())).thenAnswer(answer -> {
            Consumer<RedisOperations<String, Object>> callback = answer.getArgument(0);
            callback.accept(operations);
            return List.of();
        });
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("batch-write")
                        .strongConsistency(false)
                        .redisBatchSize(2)
                        .build(),
                redisUtil,
                mock(CacheSerializer.class),
                publisher,
                "node-a",
                () -> { }
        );

        cache.putAll(Map.of("a", "1", "b", "2", "c", "3"), Duration.ofMinutes(5));

        verify(redisUtil, times(2)).pipeline(any());
        verify(publisher, times(1)).publish(argThat(message ->
                message.getKeys().size() == 3 && !message.isAll()));
        assertEquals("1", cache.getIfPresent("a"));
        assertEquals("2", cache.getIfPresent("b"));
        assertEquals("3", cache.getIfPresent("c"));
        assertEquals(1, cache.stats().getBatchWriteCount());
        assertEquals(2, cache.stats().getRedisBatchCount());
    }

    @Test
    @DisplayName("强一致批量读只接受版本和区域纪元均稳定的命中")
    @SuppressWarnings("unchecked")
    void versionedBatchReadShouldRejectUnstableEntries() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        RedisOperations<String, Object> operations = mock(RedisOperations.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(operations.opsForValue()).thenReturn(valueOperations);
        when(redisUtil.pipeline(any())).thenAnswer(answer -> {
            Consumer<RedisOperations<String, Object>> callback = answer.getArgument(0);
            callback.accept(operations);
            return List.of(
                    3L,
                    7L, "stable", 300_000L, 7L,
                    8L, "changed", 300_000L, 9L,
                    3L
            );
        });
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("versioned-batch")
                        .l1Enabled(false)
                        .strongConsistency(true)
                        .build(),
                redisUtil,
                mock(CacheSerializer.class)
        );

        Map<String, String> result = cache.getAllPresent(
                new LinkedHashSet<>(List.of("stable-key", "changed-key"))
        );

        assertEquals(Map.of("stable-key", "stable"), result);
        verify(redisUtil, times(1)).pipeline(any());
    }

    @Test
    @DisplayName("强一致批量读遇到尚未创建的区域版本时不应误判 Redis 降级")
    @SuppressWarnings("unchecked")
    void versionedBatchReadShouldAcceptMissingRegionVersion() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        RedisOperations<String, Object> operations = mock(RedisOperations.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(operations.opsForValue()).thenReturn(valueOperations);
        when(redisUtil.pipeline(any())).thenAnswer(answer -> {
            Consumer<RedisOperations<String, Object>> callback = answer.getArgument(0);
            callback.accept(operations);
            return Arrays.asList(null, 1L, "cached", 300_000L, 1L, null);
        });
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("empty-versioned-batch")
                        .l1Enabled(false)
                        .strongConsistency(true)
                        .build(),
                redisUtil,
                mock(CacheSerializer.class)
        );

        Map<String, String> result = cache.getAllPresent(Set.of("missing-key"));

        assertEquals(Map.of("missing-key", "cached"), result);
        assertFalse(cache.isL2Degraded());
    }

    @Test
    @DisplayName("强一致 putAll 在一个 pipeline 中排入多个单 Key Lua")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void versionedPutAllShouldPipelinePerKeyLuaScripts() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisScriptingCommands scriptingCommands = mock(RedisScriptingCommands.class);
        BoundValueOperations<String, Object> regionVersion = mock(BoundValueOperations.class);
        when(redisUtil.getTemplate()).thenReturn(template);
        doReturn(new StringRedisSerializer()).when(template).getKeySerializer();
        when(connection.scriptingCommands()).thenReturn(scriptingCommands);
        when(redisUtil.serializeValue(any())).thenAnswer(answer -> {
            Object value = answer.getArgument(0);
            return String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        });
        when(redisUtil.boundValueOps("letool:cache:%META%:versioned-write:region-version"))
                .thenReturn(regionVersion);
        when(regionVersion.get()).thenReturn(0L);
        when(template.executePipelined(any(RedisCallback.class))).thenAnswer(answer -> {
            RedisCallback callback = answer.getArgument(0);
            callback.doInRedis(connection);
            return List.of(1L, 2L);
        });
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                CacheConfig.<String, String>builder("versioned-write")
                        .strongConsistency(true)
                        .build(),
                redisUtil,
                mock(CacheSerializer.class),
                publisher,
                "node-a",
                () -> { }
        );

        cache.putAll(Map.of("a", "1", "b", "2"));

        verify(scriptingCommands, times(2)).eval(any(), any(), eq(2), any(byte[][].class));
        verify(publisher).publish(argThat(message -> message.getKeys().size() == 2));
        assertEquals(1, cache.stats().getRedisBatchCount());
    }

    private static List<Object> repeatedReadResults(int keyCount) {
        List<Object> results = new ArrayList<>(keyCount * 2);
        for (int index = 0; index < keyCount; index++) {
            results.add("cached");
            results.add(Duration.ofMinutes(5).toMillis());
        }
        return results;
    }
}
