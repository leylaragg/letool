package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.tool.redis.RedisUtil;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
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
                .redisKeyPrefix("test:cache:")
                .strongConsistency(false)
                .build();
    }

    @Test
    @DisplayName("put 写入 L1 和 Redis Hash")
    void putWritesLocalAndRedisHash() {
        when(redisUtil.boundHashOps("test:cache:profiles:user:1")).thenReturn(boundHashOperations);
        when(boundHashOperations.get("name")).thenReturn("Leyland");
        MultiLevelHashCache<String, String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateHashCache(config, Function.identity(), String.class, String.class);

        cache.put("user:1", "name", "Leyland");

        assertEquals("Leyland", cache.get("user:1", "name"));
        verify(boundHashOperations).put("name", "Leyland");
        verify(redisUtil).expire("test:cache:profiles:user:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
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
        when(redisUtil.boundHashOps("test:cache:profiles:user:2")).thenReturn(boundHashOperations);
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
        when(redisUtil.boundHashOps("test:cache:profiles:user:3")).thenReturn(boundHashOperations);
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
        when(redisUtil.boundHashOps("test:cache:profiles:user:4")).thenReturn(boundHashOperations);
        when(boundHashOperations.entries()).thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisUtil, serializer);
        MultiLevelHashCache<String, String, String> cache =
                manager.getOrCreateHashCache(config, Function.identity(), String.class, String.class);

        assertTrue(cache.entries("user:4").isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }

    @Test
    @DisplayName("局部写入不能让 L1 冒充完整 Redis Hash 快照")
    void partialPutShouldNotHideExistingRedisFields() {
        when(redisUtil.boundHashOps("test:cache:profiles:user:5"))
                .thenReturn(boundHashOperations);
        when(boundHashOperations.entries())
                .thenReturn(Map.of("name", "Leyland", "role", "admin"));
        MultiLevelHashCache<String, String, String> cache =
                new CacheManager(redisUtil, serializer)
                        .getOrCreateHashCache(
                                config,
                                Function.identity(),
                                String.class,
                                String.class
                        );

        cache.put("user:5", "name", "Leyland");

        assertEquals(
                Map.of("name", "Leyland", "role", "admin"),
                cache.entries("user:5")
        );
        verify(boundHashOperations).entries();
    }

    @Test
    @DisplayName("强一致读取应把 Redis 空 Hash 视为权威结果")
    void strongConsistencyShouldNotReturnStaleHashWhenRedisIsEmpty() {
        CacheConfig<String, String> strongConfig =
                CacheConfig.<String, String>builder("profiles")
                        .l1Ttl(Duration.ofMinutes(10))
                        .l2Ttl(Duration.ofHours(1))
                        .redisKeyPrefix("test:cache:")
                        .strongConsistency(true)
                        .build();
        when(redisUtil.boundHashOps("test:cache:profiles:user:6"))
                .thenReturn(boundHashOperations);
        when(boundHashOperations.entries())
                .thenReturn(Map.of("name", "old"), Map.of());
        MultiLevelHashCache<String, String, String> cache =
                new CacheManager(redisUtil, serializer)
                        .getOrCreateHashCache(
                                strongConfig,
                                Function.identity(),
                                String.class,
                                String.class
                        );

        assertEquals(Map.of("name", "old"), cache.entries("user:6"));
        assertTrue(cache.entries("user:6").isEmpty());
    }

    @Test
    @DisplayName("反序列化类型不匹配的 Hash 字段不应直接强转")
    void typeMismatchShouldBeIgnored() {
        when(redisUtil.boundHashOps("test:cache:profiles:user:7"))
                .thenReturn(boundHashOperations);
        when(boundHashOperations.entries())
                .thenReturn(Map.of("age", 18));
        MultiLevelHashCache<String, String, String> cache =
                new CacheManager(redisUtil, serializer)
                        .getOrCreateHashCache(
                                config,
                                Function.identity(),
                                String.class,
                                String.class
                        );

        assertTrue(cache.entries("user:7").isEmpty());
        assertTrue(cache.entries("user:7").isEmpty());
    }

    @Test
    @DisplayName("未显式指定 Hash value 类型时应使用缓存配置类型")
    void configuredValueTypeShouldBeUsedAsHashValueType() {
        CacheConfig<String, String> typedConfig =
                CacheConfig.<String, String>builder("typed-hash")
                        .redisKeyPrefix("test:cache:")
                        .strongConsistency(false)
                        .valueType(String.class)
                        .build();
        when(redisUtil.boundHashOps("test:cache:typed-hash:key"))
                .thenReturn(boundHashOperations);
        when(boundHashOperations.entries()).thenReturn(Map.of("age", 18));
        MultiLevelHashCache<String, String, String> cache =
                new CacheManager(redisUtil, serializer)
                        .getOrCreateHashCache(
                                typedConfig,
                                Function.identity(),
                                String.class,
                                null
                        );

        assertTrue(cache.entries("key").isEmpty());
    }

    /**
     * 验证区域清理会删除本地 Hash 快照并广播全区域失效消息。
     */
    @Test
    @DisplayName("evictAll 清理 Hash 区域并广播失效")
    void evictAllShouldClearLocalHashAndPublishInvalidation() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        MultiLevelHashCache<String, String, String> cache = new CacheManager(
                null,
                serializer,
                true,
                false,
                "test:cache:",
                publisher
        ).getOrCreateHashCache(config, Function.identity(), String.class, String.class);
        cache.put("user:9", "name", "Leyland");

        cache.evictAll();

        assertTrue(cache.entries("user:9").isEmpty());
        verify(publisher).publish(argThat(message ->
                message.isAll() && "profiles".equals(message.getCacheName())));
    }
}
