package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.exception.CacheException;
import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CacheManager 缓存管理器测试")
@ExtendWith(MockitoExtension.class)
class CacheManagerTest {

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private CacheSerializer serializer;

    private CacheManager manager;

    @BeforeEach
    void setUp() {
        manager = new CacheManager(redisUtil, serializer);
    }

    @Test
    @DisplayName("getOrCreate 创建新缓存实例")
    void testGetOrCreate() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("test").build();
        MultiLevelCache<String, String> cache = manager.getOrCreate(config);
        assertNotNull(cache);
        assertEquals("test", cache.getName());
    }

    @Test
    @DisplayName("未显式设置前缀时应继承管理器全局前缀")
    void shouldInheritManagerRedisKeyPrefixWhenCachePrefixIsNotExplicit() {
        CacheManager prefixedManager = new CacheManager(
                redisUtil,
                serializer,
                true,
                true,
                "edc:mlc:",
                CacheInvalidationPublisher.noop()
        );

        MultiLevelCache<String, String> cache = prefixedManager.getOrCreate(
                CacheConfig.<String, String>builder("runtime-rules").build()
        );

        assertEquals("edc:mlc:", extractConfig(cache).getRedisKeyPrefix());
    }

    @Test
    @DisplayName("getOrCreate - 相同名称返回已有实例")
    void testGetOrCreateSameName() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("dup").build();
        MultiLevelCache<String, String> first = manager.getOrCreate(config);
        MultiLevelCache<String, String> second = manager.getOrCreate(config);
        assertSame(first, second);
    }

    @Test
    @DisplayName("get 获取已注册的缓存实例")
    void testGet() {
        CacheConfig<Integer, String> config = CacheConfig.<Integer, String>builder("intCache").build();
        manager.getOrCreate(config);
        MultiLevelCache<Integer, String> cache = manager.get("intCache");
        assertNotNull(cache);
        assertEquals("intCache", cache.getName());
    }

    @Test
    @DisplayName("get 未注册名称时应返回安全的稳定错误码")
    void testGetNotFound() {
        CacheException ex = assertThrows(CacheException.class, () -> manager.get("nonexistent"));
        assertEquals("CACHE_002", ex.getCode());
        assertFalse(ex.getMessage().contains("nonexistent"));
    }

    @Test
    @DisplayName("remove 移除缓存实例")
    void testRemove() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("toRemove").build();
        manager.getOrCreate(config);
        manager.remove("toRemove");
        assertThrows(CacheException.class, () -> manager.get("toRemove"));
    }

    @Test
    @DisplayName("getAll 返回所有缓存实例")
    void testGetAll() {
        assertEquals(0, manager.getAll().size());

        manager.getOrCreate(CacheConfig.<String, String>builder("c1").build());
        manager.getOrCreate(CacheConfig.<String, String>builder("c2").build());
        manager.getOrCreate(CacheConfig.<String, String>builder("c3").build());

        Collection<MultiLevelCache<?, ?>> all = manager.getAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("同一缓存名称不能注册为不同数据结构")
    void sameNameShouldNotBeRegisteredWithDifferentCacheKinds() {
        CacheConfig<String, String> config =
                CacheConfig.<String, String>builder("shared-name").build();
        manager.getOrCreate(config);

        CacheException exception = assertThrows(
                CacheException.class,
                () -> manager.getOrCreateSetCache(config)
        );

        assertEquals("CACHE_005", exception.getCode());
        assertFalse(exception.getMessage().contains("shared-name"));
    }

    @Test
    @DisplayName("移除缓存后名称可以重新用于另一种数据结构")
    void removedNameShouldBeReusableByAnotherCacheKind() {
        CacheConfig<String, String> config =
                CacheConfig.<String, String>builder("reusable-name").build();
        manager.getOrCreate(config);

        manager.remove("reusable-name");

        assertNotNull(manager.getOrCreateSetCache(config));
    }

    @Test
    @DisplayName("失效广播应按序列化表示清理非 String KV key")
    void invalidationShouldEvictNonStringKvKey() {
        CacheManager localManager = new CacheManager(null, serializer);
        MultiLevelCache<Long, String> cache = localManager.getOrCreate(
                CacheConfig.<Long, String>builder("long-kv").build()
        );
        cache.put(7L, "cached");

        localManager.evictLocal("long-kv", "7");

        assertEquals("loaded", cache.getOrLoad(7L, key -> "loaded"));
    }

    @Test
    @DisplayName("失效广播应按自定义序列化表示清理集合 key")
    void invalidationShouldEvictCustomSerializedCollectionKey() {
        CacheManager localManager = new CacheManager(null, serializer);
        CacheConfig<Long, String> config =
                CacheConfig.<Long, String>builder("long-set").build();
        MultiLevelSetCache<Long, String> cache =
                localManager.getOrCreateSetCache(
                        config,
                        key -> "id:" + key,
                        String.class
                );
        cache.add(7L, "member");

        localManager.evictLocal("long-set", "id:7");

        assertEquals(Set.of(), cache.getMembers(7L));
    }

    private CacheConfig<?, ?> extractConfig(MultiLevelCache<?, ?> cache) {
        try {
            Field field = MultiLevelCache.class.getDeclaredField("config");
            field.setAccessible(true);
            return (CacheConfig<?, ?>) field.get(cache);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法读取缓存最终配置", exception);
        }
    }
}
