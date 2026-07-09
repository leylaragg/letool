package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.BoundZSetOperations;

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MultiLevelZSetCache 测试")
@ExtendWith(MockitoExtension.class)
class MultiLevelZSetCacheTest {

    @Mock
    private RedisUtil redisUtil;

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
                .redisKeyPrefix("test:zset:")
                .strongConsistency(false)
                .build();
    }

    @Test
    @DisplayName("add 写入 L1 和 Redis ZSet")
    void addWritesLocalAndRedisZSet() {
        when(redisUtil.boundZSetOps("test:zset:game:1")).thenReturn(boundZSetOperations);
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateZSetCache(config, Function.identity(), String.class);

        cache.add("game:1", "alice", 100.0);

        assertEquals(Set.of("alice"), cache.range("game:1", 0, -1));
        assertEquals(100.0, cache.score("game:1", "alice"));
        verify(boundZSetOperations).add("alice", 100.0);
        verify(redisUtil).expire("test:zset:game:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
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
        when(redisUtil.boundZSetOps("test:zset:game:2")).thenReturn(boundZSetOperations);
        when(boundZSetOperations.range(0, -1)).thenReturn(Set.of("alice", "bob"));
        when(boundZSetOperations.score("alice")).thenReturn(100.0);
        when(boundZSetOperations.score("bob")).thenReturn(200.0);
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateZSetCache(config, Function.identity(), String.class);

        assertEquals(Set.of("alice", "bob"), cache.range("game:2", 0, -1));
        assertEquals(200.0, cache.score("game:2", "bob"));

        verify(boundZSetOperations).range(0, -1);
    }

    @Test
    @DisplayName("remove 删除 Redis ZSet 成员并清理本地副本")
    void removeDeletesMemberAndEvictsLocalSnapshot() {
        when(redisUtil.boundZSetOps("test:zset:game:3")).thenReturn(boundZSetOperations);
        MultiLevelZSetCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateZSetCache(config, Function.identity(), String.class);

        cache.add("game:3", "alice", 100.0);
        cache.remove("game:3", "alice");

        assertNull(cache.score("game:3", "alice"));
        verify(boundZSetOperations).remove("alice");
    }

    @Test
    @DisplayName("Redis 异常后 ZSet 缓存进入 L2 降级")
    void redisFailureMarksZSetCacheDegraded() {
        when(redisUtil.boundZSetOps("test:zset:game:4")).thenReturn(boundZSetOperations);
        when(boundZSetOperations.range(0, -1)).thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisUtil, serializer);
        MultiLevelZSetCache<String, String> cache = manager.getOrCreateZSetCache(config, Function.identity(), String.class);

        assertTrue(cache.range("game:4", 0, -1).isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }
}
