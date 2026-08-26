package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.exception.CacheException;
import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.redis.RedisFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("MultiLevelZSetCache 测试")
@ExtendWith(MockitoExtension.class)
class MultiLevelZSetCacheTest {

    @Mock
    private RedisFacade redisFacade;

    @Mock
    private CacheSerializer serializer;

    @Mock
    private BoundZSetOperations<String, Object> boundZSetOperations;

    private CacheConfig<String, String> config;

    @BeforeEach
    void setUp() {
        config = CacheConfig.<String, String>builder("ranking")
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisKeyPrefix("test:cache:")
                .strongConsistency(false)
                .build();
        lenient().when(boundZSetOperations.add(any(), anyDouble())).thenReturn(true);
        lenient().when(boundZSetOperations.remove(any())).thenReturn(0L);
        lenient().when(redisFacade.expire(any(), anyLong(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("严格写策略下 ZADD 失败必须保留已有 L1")
    void strictAddShouldExposeFailureWithoutChangingLocalZSet() {
        String redisKey = "test:cache:ranking:game:strict-add";
        when(redisFacade.boundZSetOps(redisKey)).thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenReturn(Set.of(tuple("alice", 100.0)));
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateZSetCache(strictWriteConfig(), Function.identity(), String.class);
        assertEquals(Set.of("alice"), cache.range("game:strict-add", 0, -1));
        RuntimeException redisFailure = new RuntimeException("zadd failed");
        when(boundZSetOperations.add("bob", 200.0)).thenThrow(redisFailure);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.add("game:strict-add", "bob", 200.0));

        assertSame(redisFailure, thrown.getCause());
        assertEquals(Set.of("alice"), cache.range("game:strict-add", 0, -1));
    }

    @Test
    @DisplayName("严格写策略下 ZREM 失败必须保留已有成员")
    void strictRemoveShouldExposeFailureWithoutChangingLocalZSet() {
        String redisKey = "test:cache:ranking:game:strict-remove";
        when(redisFacade.boundZSetOps(redisKey)).thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenReturn(Set.of(tuple("alice", 100.0)));
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateZSetCache(strictWriteConfig(), Function.identity(), String.class);
        assertEquals(Set.of("alice"), cache.range("game:strict-remove", 0, -1));
        RuntimeException redisFailure = new RuntimeException("zrem failed");
        when(boundZSetOperations.remove("alice")).thenThrow(redisFailure);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.remove("game:strict-remove", "alice"));

        assertSame(redisFailure, thrown.getCause());
        assertEquals(Set.of("alice"), cache.range("game:strict-remove", 0, -1));
    }

    @Test
    @DisplayName("严格写策略下删除整个 ZSet 失败必须保留已有 L1")
    void strictRemoveKeyShouldExposeFailureWithoutChangingLocalZSet() {
        String redisKey = "test:cache:ranking:game:strict-remove-key";
        when(redisFacade.boundZSetOps(redisKey)).thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenReturn(Set.of(tuple("alice", 100.0)));
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateZSetCache(strictWriteConfig(), Function.identity(), String.class);
        assertEquals(Set.of("alice"), cache.range("game:strict-remove-key", 0, -1));
        RuntimeException redisFailure = new RuntimeException("del failed");
        when(redisFacade.delete(redisKey)).thenThrow(redisFailure);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.removeKey("game:strict-remove-key"));

        assertSame(redisFailure, thrown.getCause());
        assertEquals(Set.of("alice"), cache.range("game:strict-remove-key", 0, -1));
    }

    @Test
    @DisplayName("严格写策略下 ZSet TTL 失败必须暴露")
    void strictAddShouldRejectFailedTtl() {
        String redisKey = "test:cache:ranking:game:strict-ttl";
        when(redisFacade.boundZSetOps(redisKey)).thenReturn(boundZSetOperations);
        when(redisFacade.expire(redisKey, Duration.ofHours(1).toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(false);
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateZSetCache(strictWriteConfig(), Function.identity(), String.class);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.add("game:strict-ttl", "alice", 100.0));

        assertEquals("CACHE_006", thrown.getCode());
        assertTrue(cache.range("game:strict-ttl", 0, -1).isEmpty());
    }

    @Test
    @DisplayName("add 写入 L1 和 Redis ZSet")
    void addWritesLocalAndRedisZSet() {
        when(redisFacade.boundZSetOps("test:cache:ranking:game:1")).thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenReturn(Set.of(tuple("alice", 100.0)));
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateZSetCache(config, Function.identity(), String.class);

        cache.add("game:1", "alice", 100.0);

        assertEquals(Set.of("alice"), cache.range("game:1", 0, -1));
        assertEquals(100.0, cache.score("game:1", "alice"));
        verify(boundZSetOperations).add("alice", 100.0);
        verify(redisFacade).expire("test:cache:ranking:game:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("range 返回快照，调用方修改不会污染 L1")
    void rangeReturnsSnapshot() {
        MultiLevelZSetCache<String, String> cache = new CacheManager(null, serializer)
                .getOrCreateZSetCache(config, Function.identity(), String.class);

        cache.add("game:1", "alice", 100.0);
        Set<String> values = cache.range("game:1", 0, -1);
        values.add("external");

        assertEquals(Set.of("alice"), cache.range("game:1", 0, -1));
    }

    @Test
    @DisplayName("L1 miss 时从 Redis ZSet 读取并回填")
    void l1MissReadsRedisZSetAndRefillsLocal() {
        when(redisFacade.boundZSetOps("test:cache:ranking:game:2")).thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenReturn(Set.of(
                        tuple("alice", 100.0),
                        tuple("bob", 200.0)
                ));
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateZSetCache(config, Function.identity(), String.class);

        assertEquals(Set.of("alice", "bob"), cache.range("game:2", 0, -1));
        assertEquals(200.0, cache.score("game:2", "bob"));

        verify(boundZSetOperations).rangeWithScores(0, -1);
        verify(boundZSetOperations, never()).score("alice");
    }

    @Test
    @DisplayName("remove 删除 Redis ZSet 成员并清理本地副本")
    void removeDeletesMemberAndEvictsLocalSnapshot() {
        when(redisFacade.boundZSetOps("test:cache:ranking:game:3")).thenReturn(boundZSetOperations);
        when(boundZSetOperations.score("alice")).thenReturn(null);
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateZSetCache(config, Function.identity(), String.class);

        cache.add("game:3", "alice", 100.0);
        cache.remove("game:3", "alice");

        assertNull(cache.score("game:3", "alice"));
        verify(boundZSetOperations).remove("alice");
    }

    @Test
    @DisplayName("Redis 异常后 ZSet 缓存进入 L2 降级")
    void redisFailureMarksZSetCacheDegraded() {
        when(redisFacade.boundZSetOps("test:cache:ranking:game:4")).thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisFacade, serializer);
        MultiLevelZSetCache<String, String> cache = manager.getOrCreateZSetCache(config, Function.identity(), String.class);

        assertTrue(cache.range("game:4", 0, -1).isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }

    @Test
    @DisplayName("局部写入不能让 L1 冒充完整 Redis ZSet 快照")
    void partialAddShouldNotHideExistingRedisMembers() {
        when(redisFacade.boundZSetOps("test:cache:ranking:game:5"))
                .thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenReturn(Set.of(
                        tuple("alice", 100.0),
                        tuple("bob", 200.0)
                ));
        MultiLevelZSetCache<String, String> cache =
                new CacheManager(redisFacade, serializer)
                        .getOrCreateZSetCache(
                                config,
                                Function.identity(),
                                String.class
                        );

        cache.add("game:5", "alice", 100.0);

        assertEquals(
                Set.of("alice", "bob"),
                cache.range("game:5", 0, -1)
        );
        verify(boundZSetOperations).rangeWithScores(0, -1);
    }

    @Test
    @DisplayName("强一致读取应把 Redis 空 ZSet 视为权威结果")
    void strongConsistencyShouldNotReturnStaleZSetWhenRedisIsEmpty() {
        CacheConfig<String, String> strongConfig =
                CacheConfig.<String, String>builder("ranking")
                        .l1Ttl(Duration.ofMinutes(10))
                        .l2Ttl(Duration.ofHours(1))
                        .redisKeyPrefix("test:cache:")
                        .strongConsistency(true)
                        .build();
        when(redisFacade.boundZSetOps("test:cache:ranking:game:6"))
                .thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenReturn(
                        Set.of(tuple("old", 100.0)),
                        Set.of()
                );
        MultiLevelZSetCache<String, String> cache =
                new CacheManager(redisFacade, serializer)
                        .getOrCreateZSetCache(
                                strongConfig,
                                Function.identity(),
                                String.class
                        );

        assertEquals(Set.of("old"), cache.range("game:6", 0, -1));
        assertTrue(cache.range("game:6", 0, -1).isEmpty());
    }

    @Test
    @DisplayName("L1 ZSet 负索引范围应与 Redis ZRANGE 一致")
    void localRangeShouldSupportRedisNegativeIndexes() {
        MultiLevelZSetCache<String, String> cache =
                new CacheManager(null, serializer)
                        .getOrCreateZSetCache(
                                config,
                                Function.identity(),
                                String.class
                        );
        cache.add("game:7", "a", 10);
        cache.add("game:7", "b", 20);
        cache.add("game:7", "c", 30);
        cache.add("game:7", "d", 40);

        assertEquals(
                Set.of("b", "c"),
                cache.range("game:7", -3, -2)
        );
    }

    @Test
    @DisplayName("默认 ZSet 工厂应使用配置的成员类型")
    void defaultFactoryShouldUseConfiguredMemberType() {
        CacheConfig<String, String> typedConfig =
                CacheConfig.<String, String>builder("typed-zset")
                        .redisKeyPrefix("test:cache:")
                        .strongConsistency(false)
                        .valueType(String.class)
                        .build();
        when(redisFacade.boundZSetOps("test:cache:typed-zset:key"))
                .thenReturn(boundZSetOperations);
        when(boundZSetOperations.rangeWithScores(0, -1))
                .thenReturn(Set.of(tuple(42, 100.0)));
        MultiLevelZSetCache<String, String> cache =
                new CacheManager(redisFacade, serializer)
                        .getOrCreateZSetCache(typedConfig);

        assertTrue(cache.range("key", 0, -1).isEmpty());
    }

    /**
     * 验证区域清理会删除本地 ZSet 快照并广播全区域失效消息。
     */
    @Test
    @DisplayName("evictAll 清理 ZSet 区域并广播失效")
    void evictAllShouldClearLocalZSetAndPublishInvalidation() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        MultiLevelZSetCache<String, String> cache = new CacheManager(
                null,
                serializer,
                true,
                false,
                "test:cache:",
                publisher
        ).getOrCreateZSetCache(config, Function.identity(), String.class);
        cache.add("game:9", "alice", 100.0);

        cache.evictAll();

        assertTrue(cache.range("game:9", 0, -1).isEmpty());
        verify(publisher).publish(argThat(message ->
                message.isAll() && "ranking".equals(message.getCacheName())));
    }

    /** Redis 区域清理失败时不得向其它节点广播不真实的全区域失效。 */
    @Test
    @DisplayName("evictAll 清理 Redis 失败时不广播 ALL")
    void evictAllShouldNotPublishWhenRedisCleanupFails() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        RuntimeException cleanupFailure = new RuntimeException("scan failed");
        when(redisFacade.getTemplate()).thenThrow(cleanupFailure);
        MultiLevelZSetCache<String, String> cache = new CacheManager(
                redisFacade,
                serializer,
                true,
                true,
                "test:cache:",
                publisher
        ).getOrCreateZSetCache(config, Function.identity(), String.class);

        CacheException exception = assertThrows(CacheException.class, cache::evictAll);

        assertEquals("CACHE_006", exception.getCode());
        assertSame(cleanupFailure, exception.getCause());
        assertTrue(cache.isL2Degraded());
        verifyNoInteractions(publisher);
    }

    /**
     * 创建测试用 Redis ZSet 成员及分数元组。
     *
     * @param value 成员值
     * @param score 成员分数
     * @param <T> 成员类型
     * @return Spring Data Redis 带分数元组
     */
    private static <T> ZSetOperations.TypedTuple<Object> tuple(
            T value,
            double score) {
        return ZSetOperations.TypedTuple.of(value, score);
    }

    /** 创建只要求 Redis 变更可确认的 ZSet 配置。 */
    private CacheConfig<String, String> strictWriteConfig() {
        return CacheConfig.<String, String>builder("ranking")
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisKeyPrefix("test:cache:")
                .strongConsistency(false)
                .writeFailurePolicy(CacheWriteFailurePolicy.FAIL_CLOSED)
                .build();
    }
}
