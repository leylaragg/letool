package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.exception.CacheException;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

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
    @DisplayName("get - 未注册的名称抛出 CacheException")
    void testGetNotFound() {
        CacheException ex = assertThrows(CacheException.class, () -> manager.get("nonexistent"));
        assertTrue(ex.getMessage().contains("nonexistent"));
        assertTrue(ex.getMessage().contains("getOrCreate"));
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
}
