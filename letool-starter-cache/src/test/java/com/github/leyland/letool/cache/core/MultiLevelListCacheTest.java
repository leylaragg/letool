package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.BoundListOperations;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MultiLevelListCache 测试")
@ExtendWith(MockitoExtension.class)
class MultiLevelListCacheTest {

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private CacheSerializer serializer;

    @Mock
    private BoundListOperations<String, Object> boundListOperations;

    private CacheConfig<String, String> config;

    @BeforeEach
    void setUp() {
        config = CacheConfig.<String, String>builder("events")
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisKeyPrefix("test:list:")
                .strongConsistency(false)
                .build();
    }

    @Test
    @DisplayName("rightPush 写入 L1 和 Redis List")
    void rightPushWritesLocalAndRedisList() {
        when(redisUtil.boundListOps("test:list:user:1")).thenReturn(boundListOperations);
        MultiLevelListCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateListCache(config, Function.identity(), String.class);

        cache.rightPush("user:1", "login");

        assertEquals(List.of("login"), cache.range("user:1", 0, -1));
        verify(boundListOperations).rightPush("login");
        verify(redisUtil).expire("test:list:user:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("range 返回快照，调用方修改不会污染 L1")
    void rangeReturnsSnapshot() {
        MultiLevelListCache<String, String> cache = new CacheManager(null, serializer)
                .getOrCreateListCache(config, Function.identity(), String.class);

        cache.rightPush("user:1", "a");
        cache.rightPush("user:1", "b");
        List<String> values = cache.range("user:1", 0, -1);
        values.add("external");

        assertEquals(List.of("a", "b"), cache.range("user:1", 0, -1));
    }

    @Test
    @DisplayName("L1 miss 时从 Redis List 读取并回填")
    void l1MissReadsRedisListAndRefillsLocal() {
        when(redisUtil.boundListOps("test:list:user:2")).thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1)).thenReturn(List.of("a", "b"));
        MultiLevelListCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateListCache(config, Function.identity(), String.class);

        assertEquals(List.of("a", "b"), cache.range("user:2", 0, -1));
        assertEquals(List.of("a", "b"), cache.range("user:2", 0, -1));

        verify(boundListOperations).range(0, -1);
    }

    @Test
    @DisplayName("pop 从 Redis List 弹出元素并清理本地副本")
    void popUsesRedisListAndEvictsLocalSnapshot() {
        when(redisUtil.boundListOps("test:list:user:3")).thenReturn(boundListOperations);
        when(boundListOperations.leftPop()).thenReturn("first");
        MultiLevelListCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateListCache(config, Function.identity(), String.class);

        cache.rightPush("user:3", "first");
        String value = cache.leftPop("user:3");

        assertEquals("first", value);
        assertNull(cache.range("user:3", 0, -1).stream().findFirst().orElse(null));
        verify(boundListOperations).leftPop();
    }

    @Test
    @DisplayName("Redis 异常后 List 缓存进入 L2 降级")
    void redisFailureMarksListCacheDegraded() {
        when(redisUtil.boundListOps("test:list:user:4")).thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1)).thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisUtil, serializer);
        MultiLevelListCache<String, String> cache = manager.getOrCreateListCache(config, Function.identity(), String.class);

        assertTrue(cache.range("user:4", 0, -1).isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }
}
