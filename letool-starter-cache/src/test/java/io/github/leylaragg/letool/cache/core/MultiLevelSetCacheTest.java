package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.cache.exception.CacheException;
import io.github.leylaragg.letool.redis.RedisFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.BoundSetOperations;

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("MultiLevelSetCache 测试")
@ExtendWith(MockitoExtension.class)
class MultiLevelSetCacheTest {

    @Mock
    private RedisFacade redisFacade;

    @Mock
    private CacheSerializer serializer;

    @Mock
    private BoundSetOperations<String, Object> boundSetOperations;

    private CacheConfig<String, String> config;

    @BeforeEach
    void setUp() {
        config = CacheConfig.<String, String>builder("rule:index")
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisKeyPrefix("test:cache:")
                .strongConsistency(false)
                .build();
        lenient().when(boundSetOperations.add(any())).thenReturn(1L);
        lenient().when(boundSetOperations.add(any(Object[].class))).thenReturn(1L);
        lenient().when(boundSetOperations.remove(any())).thenReturn(0L);
        lenient().when(redisFacade.expire(any(), anyLong(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("严格写策略下 SADD 异常必须暴露且不污染已有 L1")
    void strictAddShouldExposeRedisFailureWithoutChangingLocalSnapshot() {
        String redisKey = "test:cache:rule%3Aindex:project:strict-add";
        when(redisFacade.boundSetOps(redisKey)).thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenReturn(Set.of("rule-a"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strictWriteConfig(), Function.identity(), String.class);
        assertEquals(Set.of("rule-a"), cache.getMembers("project:strict-add"));
        RuntimeException redisFailure = new RuntimeException("sadd failed");
        when(boundSetOperations.add("rule-b")).thenThrow(redisFailure);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.add("project:strict-add", "rule-b"));

        assertEquals("CACHE_006", thrown.getCode());
        assertSame(redisFailure, thrown.getCause());
        assertEquals(Set.of("rule-a"), cache.getMembers("project:strict-add"));
    }

    @Test
    @DisplayName("严格写策略下 SADD 成功但 TTL 失败仍必须暴露")
    void strictAddShouldRejectFailedTtl() {
        String redisKey = "test:cache:rule%3Aindex:project:strict-ttl";
        when(redisFacade.boundSetOps(redisKey)).thenReturn(boundSetOperations);
        when(boundSetOperations.add("rule-a")).thenReturn(1L);
        when(redisFacade.expire(redisKey, Duration.ofHours(1).toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(false);
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strictWriteConfig(), Function.identity(), String.class);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.add("project:strict-ttl", "rule-a"));

        assertEquals("CACHE_006", thrown.getCode());
        assertEquals(0, cache.estimatedSize());
    }

    @Test
    @DisplayName("严格写策略下 SREM 异常必须保留已有 L1 成员")
    void strictRemoveShouldExposeRedisFailureWithoutChangingLocalSnapshot() {
        String redisKey = "test:cache:rule%3Aindex:project:strict-remove";
        when(redisFacade.boundSetOps(redisKey)).thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenReturn(Set.of("rule-a"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strictWriteConfig(), Function.identity(), String.class);
        assertEquals(Set.of("rule-a"), cache.getMembers("project:strict-remove"));
        RuntimeException redisFailure = new RuntimeException("srem failed");
        when(boundSetOperations.remove("rule-a")).thenThrow(redisFailure);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.remove("project:strict-remove", "rule-a"));

        assertSame(redisFailure, thrown.getCause());
        assertEquals(Set.of("rule-a"), cache.getMembers("project:strict-remove"));
    }

    @Test
    @DisplayName("严格写策略下 DEL 异常必须保留已有 L1 集合")
    void strictRemoveKeyShouldExposeRedisFailureWithoutClearingLocalSnapshot() {
        String redisKey = "test:cache:rule%3Aindex:project:strict-delete";
        when(redisFacade.boundSetOps(redisKey)).thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenReturn(Set.of("rule-a"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strictWriteConfig(), Function.identity(), String.class);
        assertEquals(Set.of("rule-a"), cache.getMembers("project:strict-delete"));
        RuntimeException redisFailure = new RuntimeException("del failed");
        doThrow(redisFailure).when(redisFacade).delete(redisKey);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.removeKey("project:strict-delete"));

        assertSame(redisFailure, thrown.getCause());
        assertEquals(Set.of("rule-a"), cache.getMembers("project:strict-delete"));
    }

    @Test
    @DisplayName("add 写入 L1 和 Redis Set")
    void addWritesLocalAndRedisSet() {
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:1")).thenReturn(boundSetOperations);
        when(boundSetOperations.isMember("rule-a")).thenReturn(true);
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(config, Function.identity(), String.class);

        cache.add("project:1", "rule-a");

        assertTrue(cache.contains("project:1", "rule-a"));
        verify(boundSetOperations).add("rule-a");
        verify(redisFacade).expire("test:cache:rule%3Aindex:project:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("getMembers 返回快照，调用方修改不会污染 L1")
    void getMembersReturnsSnapshot() {
        MultiLevelSetCache<String, String> cache = new CacheManager(null, serializer)
                .getOrCreateSetCache(config, Function.identity(), String.class);

        cache.addAll("project:1", Set.of("rule-a", "rule-b"));
        Set<String> members = cache.getMembers("project:1");
        members.add("external");

        assertEquals(Set.of("rule-a", "rule-b"), cache.getMembers("project:1"));
    }

    @Test
    @DisplayName("remove 删除成员并广播为本地失效语义")
    void removeDeletesMember() {
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:1")).thenReturn(boundSetOperations);
        when(boundSetOperations.isMember("rule-a")).thenReturn(false);
        when(boundSetOperations.isMember("rule-b")).thenReturn(true);
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(config, Function.identity(), String.class);

        cache.addAll("project:1", Set.of("rule-a", "rule-b"));
        cache.remove("project:1", "rule-a");

        assertFalse(cache.contains("project:1", "rule-a"));
        assertTrue(cache.contains("project:1", "rule-b"));
        verify(boundSetOperations).add(any(), any());
        verify(boundSetOperations).remove("rule-a");
    }

    @Test
    @DisplayName("L1 miss 时从 Redis Set 读取并回填")
    void l1MissReadsRedisSetAndRefillsLocal() {
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:2")).thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenReturn(Set.of("rule-c", "rule-d"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(config, Function.identity(), String.class);

        assertEquals(Set.of("rule-c", "rule-d"), cache.getMembers("project:2"));
        assertEquals(Set.of("rule-c", "rule-d"), cache.getMembers("project:2"));

        verify(boundSetOperations, times(1)).members();
    }

    @Test
    @DisplayName("Redis 异常后 Set 缓存进入 L2 降级")
    void redisFailureMarksSetCacheDegraded() {
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:3")).thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisFacade, serializer);
        MultiLevelSetCache<String, String> cache = manager.getOrCreateSetCache(config, Function.identity(), String.class);

        assertTrue(cache.getMembers("project:3").isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }

    @Test
    @DisplayName("FAIL_CLOSED 在 Redis 读取失败时抛出稳定缓存异常")
    void failClosedShouldThrowWhenRedisReadFails() {
        CacheConfig<String, String> strictConfig = strictReadConfig();
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:strict"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenThrow(new RuntimeException("redis down"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strictConfig, Function.identity(), String.class);

        CacheException exception = assertThrows(
                CacheException.class,
                () -> cache.getMembers("project:strict")
        );

        assertEquals("CACHE_006", exception.getCode());
        assertEquals(1, cache.stats().failClosedCount());
    }

    @Test
    @DisplayName("FAIL_CLOSED 不得把已有 L1 快照伪装成权威结果")
    void failClosedShouldNotReturnStaleLocalSnapshot() {
        CacheConfig<String, String> strictConfig = strictReadConfig();
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:stale"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenReturn(Set.of("rule-a"))
                .thenThrow(new RuntimeException("redis down"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strictConfig, Function.identity(), String.class);
        assertEquals(Set.of("rule-a"), cache.getMembers("project:stale"));

        CacheException exception = assertThrows(
                CacheException.class,
                () -> cache.getMembers("project:stale")
        );

        assertEquals("CACHE_006", exception.getCode());
    }

    @Test
    @DisplayName("FAIL_CLOSED 同样约束 contains 的 Redis 读取失败")
    void failClosedContainsShouldThrowWhenRedisReadFails() {
        CacheConfig<String, String> strictConfig = strictReadConfig();
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:contains"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.isMember("rule-a"))
                .thenThrow(new RuntimeException("redis down"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strictConfig, Function.identity(), String.class);

        CacheException exception = assertThrows(
                CacheException.class,
                () -> cache.contains("project:contains", "rule-a")
        );

        assertEquals("CACHE_006", exception.getCode());
    }

    @Test
    @DisplayName("Redis 空集合是带 AUTHORITATIVE 状态的权威结果")
    void emptyRedisSetShouldBeAuthoritative() {
        CacheConfig<String, String> strongConfig = strongReadConfig(
                CacheReadFailurePolicy.STALE_IF_AVAILABLE
        );
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:empty"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenReturn(Set.of());
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strongConfig, Function.identity(), String.class);

        SetCacheReadResult<String> result = cache.getMembersWithStatus("project:empty");

        assertTrue(result.members().isEmpty());
        assertEquals(SetCacheReadResult.State.AUTHORITATIVE, result.state());
    }

    @Test
    @DisplayName("STALE_IF_AVAILABLE 明确标记 Redis 故障后的 L1 快照")
    void stalePolicyShouldMarkLocalFallback() {
        CacheConfig<String, String> strongConfig = strongReadConfig(
                CacheReadFailurePolicy.STALE_IF_AVAILABLE
        );
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:fallback"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenReturn(Set.of("rule-a"))
                .thenThrow(new RuntimeException("redis down"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(strongConfig, Function.identity(), String.class);
        cache.getMembers("project:fallback");

        SetCacheReadResult<String> result = cache.getMembersWithStatus("project:fallback");

        assertEquals(Set.of("rule-a"), result.members());
        assertEquals(SetCacheReadResult.State.STALE, result.state());
        assertEquals(1, cache.stats().staleReadCount());
    }

    @Test
    @DisplayName("EMPTY_ON_FAILURE 忽略已有快照并标记故障空结果")
    void emptyPolicyShouldIgnoreLocalSnapshot() {
        CacheConfig<String, String> emptyConfig = strongReadConfig(
                CacheReadFailurePolicy.EMPTY_ON_FAILURE
        );
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:empty-fallback"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenReturn(Set.of("rule-a"))
                .thenThrow(new RuntimeException("redis down"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisFacade, serializer)
                .getOrCreateSetCache(emptyConfig, Function.identity(), String.class);
        cache.getMembers("project:empty-fallback");

        SetCacheReadResult<String> result = cache.getMembersWithStatus("project:empty-fallback");

        assertTrue(result.members().isEmpty());
        assertEquals(SetCacheReadResult.State.FAILURE_EMPTY, result.state());
        assertEquals(1, cache.stats().failureEmptyCount());
    }

    @Test
    @DisplayName("局部新增不能让 L1 冒充完整 Redis Set 快照")
    void partialAddShouldNotHideExistingRedisMembers() {
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:5"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenReturn(Set.of("rule-a", "rule-b"));
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisFacade, serializer)
                        .getOrCreateSetCache(
                                config,
                                Function.identity(),
                                String.class
                        );

        cache.add("project:5", "rule-a");

        assertEquals(
                Set.of("rule-a", "rule-b"),
                cache.getMembers("project:5")
        );
        verify(boundSetOperations).members();
    }

    @Test
    @DisplayName("本地已存在成员时仍应保证 Redis 收到幂等写入")
    void repeatedAddShouldStillWriteRedis() {
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:6"))
                .thenReturn(boundSetOperations);
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisFacade, serializer)
                        .getOrCreateSetCache(
                                config,
                                Function.identity(),
                                String.class
                        );

        cache.add("project:6", "rule-a");
        cache.add("project:6", "rule-a");

        verify(boundSetOperations, times(2)).add("rule-a");
    }

    @Test
    @DisplayName("强一致读取应把 Redis 空集合视为权威结果")
    void strongConsistencyShouldNotReturnStaleMembersWhenRedisIsEmpty() {
        CacheConfig<String, String> strongConfig =
                CacheConfig.<String, String>builder("rule:index")
                        .l1Ttl(Duration.ofMinutes(10))
                        .l2Ttl(Duration.ofHours(1))
                        .redisKeyPrefix("test:cache:")
                        .strongConsistency(true)
                        .build();
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:7"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenReturn(Set.of("rule-a"), Set.of());
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisFacade, serializer)
                        .getOrCreateSetCache(
                                strongConfig,
                                Function.identity(),
                                String.class
                        );

        assertEquals(Set.of("rule-a"), cache.getMembers("project:7"));
        assertTrue(cache.getMembers("project:7").isEmpty());
    }

    @Test
    @DisplayName("Redis 恢复后应清理降级期间产生的本地集合")
    void recoveryShouldDiscardLocalDegradedSnapshot() {
        when(redisFacade.boundSetOps("test:cache:rule%3Aindex:project:8"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenThrow(new RuntimeException("redis down"))
                .thenReturn(Set.of("remote"));
        when(redisFacade.hasKey("test:cache:%META%:rule%3Aindex:health"))
                .thenReturn(true);
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisFacade, serializer)
                        .getOrCreateSetCache(
                                config,
                                Function.identity(),
                                String.class
                        );

        assertTrue(cache.getMembers("project:8").isEmpty());
        cache.add("project:8", "local");
        assertTrue(cache.tryRecoverL2());

        assertEquals(Set.of("remote"), cache.getMembers("project:8"));
    }

    @Test
    @DisplayName("默认 Set 工厂应使用配置的成员类型而不是固定 Long")
    void defaultFactoryShouldUseConfiguredMemberType() {
        CacheConfig<String, String> typedConfig =
                CacheConfig.<String, String>builder("typed-set")
                        .redisKeyPrefix("test:cache:")
                        .strongConsistency(false)
                        .valueType(String.class)
                        .build();
        when(redisFacade.boundSetOps("test:cache:typed-set:key"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenReturn(Set.of("member"));
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisFacade, serializer)
                        .getOrCreateSetCache(typedConfig);

        assertEquals(Set.of("member"), cache.getMembers("key"));
    }

    /**
     * 验证区域清理会删除本地 Set 快照并广播全区域失效消息。
     */
    @Test
    @DisplayName("evictAll 清理 Set 区域并广播失效")
    void evictAllShouldClearLocalSetAndPublishInvalidation() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        MultiLevelSetCache<String, String> cache = new CacheManager(
                null,
                serializer,
                true,
                false,
                "test:cache:",
                publisher
        ).getOrCreateSetCache(config, Function.identity(), String.class);
        cache.add("project:9", "rule-a");

        cache.evictAll();

        assertTrue(cache.getMembers("project:9").isEmpty());
        verify(publisher).publish(argThat(message ->
                message.isAll() && "rule:index".equals(message.getCacheName())));
    }

    /** Redis 区域清理失败时不得向其它节点广播不真实的全区域失效。 */
    @Test
    @DisplayName("evictAll 清理 Redis 失败时不广播 ALL")
    void evictAllShouldNotPublishWhenRedisCleanupFails() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        RuntimeException cleanupFailure = new RuntimeException("scan failed");
        when(redisFacade.getTemplate()).thenThrow(cleanupFailure);
        MultiLevelSetCache<String, String> cache = new CacheManager(
                redisFacade,
                serializer,
                true,
                true,
                "test:cache:",
                publisher
        ).getOrCreateSetCache(config, Function.identity(), String.class);

        CacheException exception = assertThrows(CacheException.class, cache::evictAll);

        assertEquals("CACHE_006", exception.getCode());
        assertSame(cleanupFailure, exception.getCause());
        assertTrue(cache.isL2Degraded());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("evictByPrefix 只清理匹配业务 Key 并广播 PREFIX")
    void evictByPrefixShouldClearOnlyMatchingKeys() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        MultiLevelSetCache<String, String> cache = new CacheManager(
                null,
                serializer,
                true,
                false,
                "test:cache:",
                publisher
        ).getOrCreateSetCache(config, Function.identity(), String.class);
        cache.add("project:1", "rule-a");
        cache.add("project:2", "rule-b");
        cache.add("tenant:1", "rule-c");
        clearInvocations(publisher);

        cache.evictByPrefix("project:");

        assertTrue(cache.getMembers("project:1").isEmpty());
        assertTrue(cache.getMembers("project:2").isEmpty());
        assertEquals(Set.of("rule-c"), cache.getMembers("tenant:1"));
        verify(publisher).publish(argThat(message ->
                message.isPrefix() && "project:".equals(message.getPrefix())));
    }

    /**
     * 创建必须以 Redis 读取结果为准的严格配置。
     *
     * @return 严格读取配置
     */
    private CacheConfig<String, String> strictReadConfig() {
        return strongReadConfig(CacheReadFailurePolicy.FAIL_CLOSED);
    }

    /** 创建仅约束 Redis 变更结果的严格写配置。 */
    private CacheConfig<String, String> strictWriteConfig() {
        return CacheConfig.<String, String>builder("rule:index")
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisKeyPrefix("test:cache:")
                .strongConsistency(false)
                .writeFailurePolicy(CacheWriteFailurePolicy.FAIL_CLOSED)
                .build();
    }

    /**
     * 创建指定 Redis 读取失败策略的强一致配置。
     *
     * @param policy Redis 读取失败策略
     * @return 强一致读取配置
     */
    private CacheConfig<String, String> strongReadConfig(
            CacheReadFailurePolicy policy) {
        return CacheConfig.<String, String>builder("rule:index")
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisKeyPrefix("test:cache:")
                .strongConsistency(true)
                .readFailurePolicy(policy)
                .build();
    }
}
