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
                .redisKeyPrefix("test:rule:index:")
                .strongConsistency(false)
                .build();
    }

    @Test
    @DisplayName("add 写入 L1 和 Redis Set")
    void addWritesLocalAndRedisSet() {
        when(redisUtil.boundSetOps("test:rule:index:project:1")).thenReturn(boundSetOperations);
        MultiLevelSetCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateSetCache(config, Function.identity(), String.class);

        cache.add("project:1", "rule-a");

        assertTrue(cache.contains("project:1", "rule-a"));
        verify(boundSetOperations).add("rule-a");
        verify(redisUtil).expire("test:rule:index:project:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
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
        when(redisUtil.boundSetOps("test:rule:index:project:1")).thenReturn(boundSetOperations);
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
        when(redisUtil.boundSetOps("test:rule:index:project:2")).thenReturn(boundSetOperations);
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
        when(redisUtil.boundSetOps("test:rule:index:project:3")).thenReturn(boundSetOperations);
        when(boundSetOperations.members()).thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisUtil, serializer);
        MultiLevelSetCache<String, String> cache = manager.getOrCreateSetCache(config, Function.identity(), String.class);

        assertTrue(cache.getMembers("project:3").isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }
}
