package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
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
    private RedisUtil redisUtil;

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
    }

    @Test
    @DisplayName("add 写入 L1 和 Redis Set")
    void addWritesLocalAndRedisSet() {
        when(redisUtil.boundSetOps("test:cache:rule%3Aindex:project:1")).thenReturn(boundSetOperations);
        when(boundSetOperations.isMember("rule-a")).thenReturn(true);
        MultiLevelSetCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateSetCache(config, Function.identity(), String.class);

        cache.add("project:1", "rule-a");

        assertTrue(cache.contains("project:1", "rule-a"));
        verify(boundSetOperations).add("rule-a");
        verify(redisUtil).expire("test:cache:rule%3Aindex:project:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
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
        when(redisUtil.boundSetOps("test:cache:rule%3Aindex:project:1")).thenReturn(boundSetOperations);
        when(boundSetOperations.isMember("rule-a")).thenReturn(false);
        when(boundSetOperations.isMember("rule-b")).thenReturn(true);
        MultiLevelSetCache<String, String> cache = new CacheManager(redisUtil, serializer)
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
        when(redisUtil.boundSetOps("test:cache:rule%3Aindex:project:2")).thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenReturn(Set.of("rule-c", "rule-d"));
        MultiLevelSetCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateSetCache(config, Function.identity(), String.class);

        assertEquals(Set.of("rule-c", "rule-d"), cache.getMembers("project:2"));
        assertEquals(Set.of("rule-c", "rule-d"), cache.getMembers("project:2"));

        verify(boundSetOperations, times(1)).members();
    }

    @Test
    @DisplayName("Redis 异常后 Set 缓存进入 L2 降级")
    void redisFailureMarksSetCacheDegraded() {
        when(redisUtil.boundSetOps("test:cache:rule%3Aindex:project:3")).thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisUtil, serializer);
        MultiLevelSetCache<String, String> cache = manager.getOrCreateSetCache(config, Function.identity(), String.class);

        assertTrue(cache.getMembers("project:3").isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }

    @Test
    @DisplayName("局部新增不能让 L1 冒充完整 Redis Set 快照")
    void partialAddShouldNotHideExistingRedisMembers() {
        when(redisUtil.boundSetOps("test:cache:rule%3Aindex:project:5"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenReturn(Set.of("rule-a", "rule-b"));
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisUtil, serializer)
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
        when(redisUtil.boundSetOps("test:cache:rule%3Aindex:project:6"))
                .thenReturn(boundSetOperations);
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisUtil, serializer)
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
        when(redisUtil.boundSetOps("test:cache:rule%3Aindex:project:7"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenReturn(Set.of("rule-a"), Set.of());
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisUtil, serializer)
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
        when(redisUtil.boundSetOps("test:cache:rule%3Aindex:project:8"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members())
                .thenThrow(new RuntimeException("redis down"))
                .thenReturn(Set.of("remote"));
        when(redisUtil.hasKey("test:cache:%META%:rule%3Aindex:health"))
                .thenReturn(true);
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisUtil, serializer)
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
        when(redisUtil.boundSetOps("test:cache:typed-set:key"))
                .thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenReturn(Set.of("member"));
        MultiLevelSetCache<String, String> cache =
                new CacheManager(redisUtil, serializer)
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
}
