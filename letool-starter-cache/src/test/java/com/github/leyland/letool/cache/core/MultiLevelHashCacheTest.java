package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.BoundHashOperations;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MultiLevelHashCache 测试")
@ExtendWith(MockitoExtension.class)
class MultiLevelHashCacheTest {

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private CacheSerializer serializer;

    @Mock
    private BoundHashOperations<String, Object, Object> boundHashOperations;

    private CacheConfig<String, String> config;

    @BeforeEach
    void setUp() {
        config = CacheConfig.<String, String>builder("profiles")
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisKeyPrefix("test:hash:")
                .strongConsistency(false)
                .build();
    }

    @Test
    @DisplayName("put 写入 L1 和 Redis Hash")
    void putWritesLocalAndRedisHash() {
        when(redisUtil.boundHashOps("test:hash:user:1")).thenReturn(boundHashOperations);
        MultiLevelHashCache<String, String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateHashCache(config, Function.identity(), String.class, String.class);

        cache.put("user:1", "name", "Leyland");

        assertEquals("Leyland", cache.get("user:1", "name"));
        verify(boundHashOperations).put("name", "Leyland");
        verify(redisUtil).expire("test:hash:user:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("entries 返回快照，调用方修改不会污染 L1")
    void entriesReturnsSnapshot() {
        MultiLevelHashCache<String, String, String> cache = new CacheManager(null, serializer)
                .getOrCreateHashCache(config, Function.identity(), String.class, String.class);

        cache.put("user:1", "name", "Leyland");
        Map<String, String> entries = cache.entries("user:1");
        entries.put("external", "dirty");

        assertEquals(Map.of("name", "Leyland"), cache.entries("user:1"));
    }

    @Test
    @DisplayName("L1 miss 时从 Redis Hash 读取并回填")
    void l1MissReadsRedisHashAndRefillsLocal() {
        when(redisUtil.boundHashOps("test:hash:user:2")).thenReturn(boundHashOperations);
        when(boundHashOperations.entries()).thenReturn(Map.of("name", "Ada"));
        MultiLevelHashCache<String, String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateHashCache(config, Function.identity(), String.class, String.class);

        assertEquals(Map.of("name", "Ada"), cache.entries("user:2"));
        assertEquals("Ada", cache.get("user:2", "name"));

        verify(boundHashOperations).entries();
    }

    @Test
    @DisplayName("delete 删除 Redis Hash 字段并清理本地副本")
    void deleteRemovesHashFieldAndEvictsLocalSnapshot() {
        when(redisUtil.boundHashOps("test:hash:user:3")).thenReturn(boundHashOperations);
        MultiLevelHashCache<String, String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateHashCache(config, Function.identity(), String.class, String.class);

        cache.put("user:3", "name", "Leyland");
        cache.delete("user:3", "name");

        assertNull(cache.get("user:3", "name"));
        verify(boundHashOperations).delete("name");
    }

    @Test
    @DisplayName("Redis 异常后 Hash 缓存进入 L2 降级")
    void redisFailureMarksHashCacheDegraded() {
        when(redisUtil.boundHashOps("test:hash:user:4")).thenReturn(boundHashOperations);
        when(boundHashOperations.entries()).thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisUtil, serializer);
        MultiLevelHashCache<String, String, String> cache =
                manager.getOrCreateHashCache(config, Function.identity(), String.class, String.class);

        assertTrue(cache.entries("user:4").isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }
}
