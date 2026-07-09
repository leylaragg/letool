package com.github.leyland.letool.cache;

import com.github.leyland.letool.cache.core.*;
import com.github.leyland.letool.cache.exception.CacheException;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.cache.support.CacheMonitor;
import com.github.leyland.letool.cache.support.CacheTemplate;
import com.github.leyland.letool.cache.support.RedisKeySerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.BoundValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 二级缓存模块综合测试.
 *
 * <p>覆盖 CacheConfig / CacheStats / NullSentinel / CacheManager / DefaultMultiLevelCache / CacheTemplate / CacheMonitor</p>
 */
@DisplayName("二级缓存模块测试")
class MultiLevelCacheTest {

    // ================================================================
    // 测试辅助
    // ================================================================

    /** 简单的值类型，用于测试序列化/反序列化 */
    static class TestValue {
        private String data;
        TestValue() {}
        TestValue(String data) { this.data = data; }
        String getData() { return data; }
        void setData(String data) { this.data = data; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestValue)) return false;
            return data != null ? data.equals(((TestValue) o).data) : ((TestValue) o).data == null;
        }
        @Override public int hashCode() { return data != null ? data.hashCode() : 0; }
        @Override public String toString() { return "TestValue{data='" + data + "'}"; }
    }

    /** 简单的 JSON 序列化器 */
    static class SimpleCacheSerializer implements CacheSerializer {
        @Override public <T> String serialize(T value) {
            if (value == null) return null;
            return "{\"data\":\"" + value.toString() + "\"}";
        }
        @SuppressWarnings("unchecked")
        @Override public <T> T deserialize(String json, Class<T> clazz) {
            if (json == null || json.isEmpty()) return null;
            // 简易反序列化：提取 "data":"..." 中的值
            String extracted = json.replaceAll(".*\"data\"\\s*:\\s*\"([^\"]*)\".*", "$1");
            if (clazz == TestValue.class) return (T) new TestValue(extracted);
            if (clazz == String.class) return (T) extracted;
            return (T) extracted;
        }
    }

    // ================================================================
    // CacheConfig 测试
    // ================================================================

    @Nested
    @DisplayName("CacheConfig 构建器")
    class CacheConfigTests {

        @Test
        @DisplayName("Builder 方式创建——设置所有属性")
        void builderAllProperties() {
            CacheConfig<String, TestValue> config = CacheConfig.<String, TestValue>builder("test")
                    .l1MaxSize(500)
                    .l1Ttl(Duration.ofMinutes(30))
                    .l2Ttl(Duration.ofHours(2))
                    .nullValueCache(false)
                    .nullValueTtl(Duration.ofSeconds(60))
                    .redisKeyPrefix("app:cache:");

            assertEquals("test", config.getName());
            assertEquals(500, config.getL1MaxSize());
            assertEquals(Duration.ofMinutes(30), config.getL1Ttl());
            assertEquals(Duration.ofHours(2), config.getL2Ttl());
            assertFalse(config.isNullValueCache());
            assertEquals(Duration.ofSeconds(60), config.getNullValueTtl());
            assertEquals("app:cache:", config.getRedisKeyPrefix());
        }

        @Test
        @DisplayName("Builder 默认值")
        void builderDefaults() {
            CacheConfig<String, TestValue> config = CacheConfig.<String, TestValue>builder("defaults");

            assertEquals(2000, config.getL1MaxSize());
            assertEquals(Duration.ofHours(24), config.getL1Ttl());
            assertEquals(Duration.ofDays(3), config.getL2Ttl());
            assertTrue(config.isNullValueCache());
            assertEquals(Duration.ofMinutes(5), config.getNullValueTtl());
            assertEquals("letool:cache:", config.getRedisKeyPrefix());
        }

        @Test
        @DisplayName("Builder 链式调用——分步设置")
        void builderChaining() {
            CacheConfig<Integer, String> config = CacheConfig.<Integer, String>builder("chained")
                    .l1MaxSize(1000)
                    .l1Ttl(Duration.ofMinutes(10))
                    .l2Ttl(Duration.ofMinutes(30))
                    .nullValueCache(true)
                    .nullValueTtl(Duration.ofMinutes(1))
                    .redisKeyPrefix("custom:");

            // 多次 getter 调用验证链式返回自身
            assertNotNull(config.getName());
            assertEquals(1000, config.getL1MaxSize());
            assertEquals(Duration.ofMinutes(10), config.getL1Ttl());
            assertEquals(Duration.ofMinutes(30), config.getL2Ttl());
            assertTrue(config.isNullValueCache());
            assertEquals(Duration.ofMinutes(1), config.getNullValueTtl());
            assertEquals("custom:", config.getRedisKeyPrefix());
        }

        @Test
        @DisplayName("Builder 泛型安全——不同 K/V 类型均可构建")
        void builderGenericTypeSafety() {
            CacheConfig<Long, TestValue> c1 = CacheConfig.<Long, TestValue>builder("long-key");
            CacheConfig<String, String> c2 = CacheConfig.<String, String>builder("string-both");
            CacheConfig<Integer, byte[]> c3 = CacheConfig.<Integer, byte[]>builder("bytes");

            assertNotNull(c1);
            assertNotNull(c2);
            assertNotNull(c3);
            assertEquals("long-key", c1.getName());
            assertEquals("string-both", c2.getName());
            assertEquals("bytes", c3.getName());
        }

        @Test
        @DisplayName("覆盖默认值——nullValueCache 设为 false")
        void nullValueCacheDisabled() {
            CacheConfig<String, String> config = CacheConfig.<String, String>builder("nc-off")
                    .nullValueCache(false);

            assertFalse(config.isNullValueCache());
        }

        @Test
        @DisplayName("NullValueTtl 独立于 L1Ttl——不同时长")
        void nullValueTtlIndependent() {
            Duration nullTtl = Duration.ofSeconds(30);
            CacheConfig<String, String> config = CacheConfig.<String, String>builder("nttl")
                    .l1Ttl(Duration.ofMinutes(60))
                    .nullValueTtl(nullTtl);

            assertEquals(Duration.ofMinutes(60), config.getL1Ttl());
            assertEquals(nullTtl, config.getNullValueTtl());
            assertNotEquals(config.getL1Ttl(), config.getNullValueTtl());
        }
    }

    // ================================================================
    // CacheStats 测试
    // ================================================================

    @Nested
    @DisplayName("CacheStats 统计计数器")
    class CacheStatsTests {

        private CacheStats stats;

        @BeforeEach
        void setUp() {
            stats = new CacheStats();
        }

        @Test
        @DisplayName("初始状态所有计数器为 0")
        void initialZeroCounts() {
            assertEquals(0, stats.getL1HitCount());
            assertEquals(0, stats.getL2HitCount());
            assertEquals(0, stats.getMissCount());
            assertEquals(0, stats.getLoadCount());
            assertEquals(0, stats.getLoadSuccessCount());
            assertEquals(0, stats.getLoadFailureCount());
            assertEquals(0, stats.getEvictionCount());
            assertEquals(0, stats.getL2DegradedCount());
            assertEquals(0, stats.getTotalRequests());
        }

        @Test
        @DisplayName("初始命中率为 0（分母为 0）")
        void initialHitRatesZero() {
            assertEquals(0.0, stats.getL1HitRate());
            assertEquals(0.0, stats.getL2HitRate());
            assertEquals(0.0, stats.getTotalHitRate());
        }

        @Test
        @DisplayName("recordL1Hit 递增 L1 命中计数")
        void recordL1Hit() {
            stats.recordL1Hit();
            stats.recordL1Hit();
            assertEquals(2, stats.getL1HitCount());
            assertEquals(0, stats.getL2HitCount());
            assertEquals(0, stats.getMissCount());
            assertEquals(2, stats.getTotalRequests());
        }

        @Test
        @DisplayName("recordL2Hit 递增 L2 命中计数")
        void recordL2Hit() {
            stats.recordL2Hit();
            assertEquals(1, stats.getL2HitCount());
            assertEquals(0, stats.getL1HitCount());
            assertEquals(1, stats.getTotalRequests());
        }

        @Test
        @DisplayName("recordMiss 递增未命中计数")
        void recordMiss() {
            stats.recordMiss();
            stats.recordMiss();
            stats.recordMiss();
            assertEquals(3, stats.getMissCount());
            assertEquals(3, stats.getTotalRequests());
        }

        @Test
        @DisplayName("totalRequests 为 L1 + L2 + miss 之和")
        void totalRequestsSum() {
            stats.recordL1Hit();
            stats.recordL1Hit();
            stats.recordL2Hit();
            stats.recordMiss();
            stats.recordMiss();
            assertEquals(5, stats.getTotalRequests());
        }

        @Test
        @DisplayName("recordLoad / recordLoadSuccess / recordLoadFailure")
        void loadCounters() {
            stats.recordLoad();
            stats.recordLoad();
            stats.recordLoadSuccess();
            stats.recordLoadFailure();
            stats.recordLoadSuccess();
            assertEquals(2, stats.getLoadCount());
            assertEquals(2, stats.getLoadSuccessCount());
            assertEquals(1, stats.getLoadFailureCount());
        }

        @Test
        @DisplayName("recordEviction 递增淘汰计数")
        void recordEviction() {
            stats.recordEviction();
            stats.recordEviction();
            assertEquals(2, stats.getEvictionCount());
        }

        @Test
        @DisplayName("recordL2Degraded 递增降级次数")
        void recordL2Degraded() {
            stats.recordL2Degraded();
            assertEquals(1, stats.getL2DegradedCount());
        }

        @Test
        @DisplayName("命中率计算——全部 L1 命中率为 1.0")
        void hitRateAllL1Hit() {
            stats.recordL1Hit();
            stats.recordL1Hit();
            assertEquals(1.0, stats.getL1HitRate(), 0.001);
            assertEquals(0.0, stats.getL2HitRate(), 0.001);
            assertEquals(1.0, stats.getTotalHitRate(), 0.001);
        }

        @Test
        @DisplayName("命中率计算——混合命中")
        void hitRateMixed() {
            stats.recordL1Hit();   // L1:1
            stats.recordL2Hit();   // L2:1
            stats.recordMiss();    // miss:1
            // total=3, L1=1/3≈0.333, L2=1/3≈0.333, total-hit=2/3≈0.667
            assertEquals(1.0 / 3.0, stats.getL1HitRate(), 0.001);
            assertEquals(1.0 / 3.0, stats.getL2HitRate(), 0.001);
            assertEquals(2.0 / 3.0, stats.getTotalHitRate(), 0.001);
        }

        @Test
        @DisplayName("线程安全——AtomicLong 保证并发计数")
        void threadSafeCounters() throws InterruptedException {
            int threadCount = 4;
            int opsPerThread = 1000;
            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                int idx = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < opsPerThread; j++) {
                        switch (idx % 3) {
                            case 0: stats.recordL1Hit(); break;
                            case 1: stats.recordL2Hit(); break;
                            case 2: stats.recordMiss(); break;
                        }
                    }
                });
            }
            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            assertEquals(threadCount * opsPerThread, stats.getTotalRequests());
        }
    }

    // ================================================================
    // NullSentinel 测试
    // ================================================================

    @Nested
    @DisplayName("NullSentinel 空值哨兵")
    class NullSentinelTests {

        @Test
        @DisplayName("toString 返回 NULL_SENTINEL")
        void toStringReturnsNullSentinel() {
            // 通过反射获取 INSTANCE
            NullSentinel sentinel = getNullSentinelInstance();
            assertEquals("NULL_SENTINEL", sentinel.toString());
        }

        @Test
        @DisplayName("NullSentinel 单例——同一实例")
        void singleton() {
            NullSentinel s1 = getNullSentinelInstance();
            NullSentinel s2 = getNullSentinelInstance();
            assertSame(s1, s2);
        }

        @Test
        @DisplayName("instanceof 检查——可用于区分哨兵和真实值")
        void instanceofCheck() {
            Object sentinel = getNullSentinelInstance();
            assertTrue(sentinel instanceof NullSentinel);
        }

        @Test
        @DisplayName("与字符串区分——不为 NULL_SENTINEL 字符串")
        void notEqualToSentinelString() {
            Object sentinel = getNullSentinelInstance();
            assertNotEquals("NULL_SENTINEL", sentinel);
        }

        /**
         * 通过反射获取 package-private 的单例.
         */
        private NullSentinel getNullSentinelInstance() {
            try {
                java.lang.reflect.Field field = NullSentinel.class.getDeclaredField("INSTANCE");
                field.setAccessible(true);
                return (NullSentinel) field.get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ================================================================
    // RedisKeySerializer 测试
    // ================================================================

    @Nested
    @DisplayName("RedisKeySerializer Key 构建")
    class RedisKeySerializerTests {

        @Test
        @DisplayName("buildKey 拼接 prefix + cacheName + : + key")
        void buildKeyStandard() {
            String key = RedisKeySerializer.buildKey("prefix:", "userCache", 123);
            assertEquals("prefix:userCache:123", key);
        }

        @Test
        @DisplayName("buildKey 字符串 key")
        void buildKeyWithStringKey() {
            String key = RedisKeySerializer.buildKey("app:", "product", "abc-001");
            assertEquals("app:product:abc-001", key);
        }

        @Test
        @DisplayName("buildKey 空 prefix")
        void buildKeyEmptyPrefix() {
            String key = RedisKeySerializer.buildKey("", "cache", "k1");
            assertEquals("cache:k1", key);
        }
    }

    // ================================================================
    // CacheManager 测试
    // ================================================================

    @Nested
    @DisplayName("CacheManager 缓存管理器注册")
    class CacheManagerTests {

        private CacheManager cacheManager;
        private SimpleCacheSerializer serializer;

        @BeforeEach
        void setUp() {
            serializer = new SimpleCacheSerializer();
            // 不传 RedisUtil 模拟 L1-only 环境
            cacheManager = new CacheManager(null, serializer);
        }

        @Test
        @DisplayName("getOrCreate 创建新缓存实例")
        void getOrCreateNew() {
            CacheConfig<String, TestValue> config = CacheConfig.<String, TestValue>builder("c1");
            MultiLevelCache<String, TestValue> cache = cacheManager.getOrCreate(config);

            assertNotNull(cache);
            assertEquals("c1", cache.getName());
        }

        @Test
        @DisplayName("getOrCreate 同一名称返回相同实例")
        void getOrCreateSameInstance() {
            CacheConfig<String, TestValue> config = CacheConfig.<String, TestValue>builder("shared");
            MultiLevelCache<String, TestValue> cache1 = cacheManager.getOrCreate(config);
            MultiLevelCache<String, TestValue> cache2 = cacheManager.getOrCreate(config);

            assertSame(cache1, cache2);
        }

        @Test
        @DisplayName("get 获取已注册缓存")
        void getExisting() {
            CacheConfig<Integer, String> config = CacheConfig.<Integer, String>builder("numCache");
            cacheManager.getOrCreate(config);
            MultiLevelCache<Integer, String> cache = cacheManager.get("numCache");

            assertNotNull(cache);
            assertEquals("numCache", cache.getName());
        }

        @Test
        @DisplayName("get 未注册缓存名抛 CacheException")
        void getNotRegisteredThrows() {
            CacheException ex = assertThrows(CacheException.class, () -> cacheManager.get("nonexistent"));
            assertTrue(ex.getMessage().contains("nonexistent"));
        }

        @Test
        @DisplayName("getAll 返回所有已注册缓存")
        void getAllReturnsAll() {
            cacheManager.getOrCreate(CacheConfig.builder("a"));
            cacheManager.getOrCreate(CacheConfig.builder("b"));
            cacheManager.getOrCreate(CacheConfig.builder("c"));

            assertEquals(3, cacheManager.getAll().size());
        }

        @Test
        @DisplayName("remove 移除缓存实例")
        void removeCache() {
            cacheManager.getOrCreate(CacheConfig.builder("temp"));
            assertNotNull(cacheManager.get("temp"));

            cacheManager.remove("temp");
            assertThrows(CacheException.class, () -> cacheManager.get("temp"));
        }

        @Test
        @DisplayName("多个不同 key 类型的缓存共存")
        void multipleKeyTypes() {
            cacheManager.getOrCreate(CacheConfig.<String, String>builder("stringKey"));
            cacheManager.getOrCreate(CacheConfig.<Long, String>builder("longKey"));
            cacheManager.getOrCreate(CacheConfig.<Integer, TestValue>builder("intKey"));

            assertEquals(3, cacheManager.getAll().size());
            assertNotNull(cacheManager.get("stringKey"));
            assertNotNull(cacheManager.get("longKey"));
            assertNotNull(cacheManager.get("intKey"));
        }
    }

    // ================================================================
    // DefaultMultiLevelCache L1-only 测试（无 Redis）
    // ================================================================

    @Nested
    @DisplayName("DefaultMultiLevelCache L1 本地缓存（无 Redis）")
    class DefaultMultiLevelCacheL1OnlyTests {

        private MultiLevelCache<String, String> cache;
        private AtomicInteger loadCount;

        @BeforeEach
        void setUp() {
            loadCount = new AtomicInteger(0);
            CacheConfig<String, String> config = CacheConfig.<String, String>builder("l1-only")
                    .l1MaxSize(100)
                    .l1Ttl(Duration.ofSeconds(10))
                    .nullValueCache(true);
            // redisUtil = null 表示仅 L1
            cache = new DefaultMultiLevelCache<>(config, null, new SimpleCacheSerializer());
        }

        @Test
        @DisplayName("getName 返回配置名称")
        void getName() {
            assertEquals("l1-only", cache.getName());
        }

        @Test
        @DisplayName("首次 getOrLoad 调用 loader 并回填缓存")
        void firstGetOrLoadInvokesLoader() {
            String result = cache.getOrLoad("key1", key -> {
                loadCount.incrementAndGet();
                return "loaded-" + key;
            });

            assertEquals("loaded-key1", result);
            assertEquals(1, loadCount.get());
        }

        @Test
        @DisplayName("第二次 getOrLoad 命中 L1 缓存，不调 loader")
        void secondGetOrLoadHitsL1Cache() {
            cache.getOrLoad("k1", k -> "val1");
            String result = cache.getOrLoad("k1", k -> {
                loadCount.incrementAndGet();
                return "should-not-load";
            });

            assertEquals("val1", result);
            assertEquals(0, loadCount.get());
        }

        @Test
        @DisplayName("同一 JVM 内相同 key 并发回源只调用一次 loader")
        void sameKeyConcurrentLoadOnlyCallsLoaderOnce() throws Exception {
            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger loads = new AtomicInteger();
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    return cache.getOrLoad("hot", key -> {
                        loads.incrementAndGet();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "loaded";
                    });
                }));
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("loaded", future.get(2, TimeUnit.SECONDS));
            }
            executor.shutdownNow();

            assertEquals(1, loads.get());
        }

        @Test
        @DisplayName("put 直接写入，随后查询命中")
        void putThenGet() {
            cache.put("k2", "direct-value");
            String result = cache.getOrLoad("k2", k -> {
                loadCount.incrementAndGet();
                return "fallback";
            });

            assertEquals("direct-value", result);
            assertEquals(0, loadCount.get());
        }

        @Test
        @DisplayName("put 指定 TTL 写入")
        void putWithCustomTtl() {
            cache.put("k3", "custom-ttl-value", Duration.ofMinutes(5));
            String result = cache.getOrLoad("k3", k -> "fallback");
            assertEquals("custom-ttl-value", result);
        }

        @Test
        @DisplayName("evict 删除后再次查询调用 loader")
        void evictThenReload() {
            cache.put("k4", "evict-me");
            cache.evict("k4");

            String result = cache.getOrLoad("k4", k -> "reloaded");
            assertEquals("reloaded", result);
        }

        @Test
        @DisplayName("loader 返回 null + nullValueCache=true 时缓存哨兵，不重复调 loader")
        void nullValueCacheEnabled() {
            String r1 = cache.getOrLoad("nil", k -> {
                loadCount.incrementAndGet();
                return null;
            });
            assertNull(r1);
            assertEquals(1, loadCount.get());

            // 第二次查询：命中 NULL_SENTINEL，不调 loader
            String r2 = cache.getOrLoad("nil", k -> {
                loadCount.incrementAndGet();
                return "should-be-ignored";
            });
            assertNull(r2);
            assertEquals(1, loadCount.get());
        }

        @Test
        @DisplayName("loader 返回 null + nullValueCache=false 时不缓存，重复调 loader")
        void nullValueCacheDisabled() {
            CacheConfig<String, String> config = CacheConfig.<String, String>builder("nc-off")
                    .nullValueCache(false);
            MultiLevelCache<String, String> ncOffCache = new DefaultMultiLevelCache<>(config, null, new SimpleCacheSerializer());
            AtomicInteger loads = new AtomicInteger(0);

            String r1 = ncOffCache.getOrLoad("nil", k -> { loads.incrementAndGet(); return null; });
            assertNull(r1);
            assertEquals(1, loads.get());

            String r2 = ncOffCache.getOrLoad("nil", k -> { loads.incrementAndGet(); return null; });
            assertNull(r2);
            assertEquals(2, loads.get()); // 未缓存 null，再次调用 loader
        }

        @Test
        @DisplayName("loader 抛异常时抛 CacheException 并记录失败")
        void loaderThrowsCacheException() {
            CacheException ex = assertThrows(CacheException.class, () ->
                    cache.getOrLoad("err", k -> { throw new RuntimeException("DB down"); })
            );
            assertTrue(ex.getMessage().contains("err"));
            assertTrue(ex.getMessage().contains("l1-only"));
            assertEquals(1, cache.stats().getLoadFailureCount());
        }

        @Test
        @DisplayName("stats 统计信息正确")
        void statsCorrectness() {
            cache.getOrLoad("a", k -> "val-a");   // miss → load
            cache.getOrLoad("a", k -> "val-a");   // L1 hit
            cache.getOrLoad("b", k -> "val-b");   // miss → load
            cache.getOrLoad("b", k -> "val-b");   // L1 hit
            cache.put("c", "val-c");              // write
            cache.evict("c");                     // evict

            CacheStats s = cache.stats();
            assertEquals(2, s.getL1HitCount());
            assertEquals(2, s.getMissCount());
            assertEquals(2, s.getLoadCount());
            assertEquals(1, s.getEvictionCount());
            assertEquals(4, s.getTotalRequests()); // 2 L1 hits + 2 misses
        }

        @Test
        @DisplayName("多 key 独立缓存互不干扰")
        void independentKeys() {
            cache.put("k1", "v1");
            cache.put("k2", "v2");
            cache.put("k3", "v3");

            assertEquals("v1", cache.getOrLoad("k1", k -> "fallback"));
            assertEquals("v2", cache.getOrLoad("k2", k -> "fallback"));
            assertEquals("v3", cache.getOrLoad("k3", k -> "fallback"));

            cache.evict("k2");
            assertEquals("v1", cache.getOrLoad("k1", k -> "fallback"));
            assertEquals("reloaded", cache.getOrLoad("k2", k -> "reloaded"));
            assertEquals("v3", cache.getOrLoad("k3", k -> "fallback"));
        }
    }

    // ================================================================
    // DefaultMultiLevelCache L2 集成测试（Mock Redis）
    // ================================================================

    @Nested
    @DisplayName("DefaultMultiLevelCache L2 Redis 集成（Mock）")
    @ExtendWith(MockitoExtension.class)
    class DefaultMultiLevelCacheL2Tests {

        @Mock
        private RedisUtil redisUtil;

        @Mock
        private BoundValueOperations<String, Object> boundValueOperations;

        private SimpleCacheSerializer serializer;
        private MultiLevelCache<Integer, String> cache;

        @BeforeEach
        void setUp() {
            serializer = new SimpleCacheSerializer();
            CacheConfig<Integer, String> config = CacheConfig.<Integer, String>builder("l2-cache")
                    .l1MaxSize(100)
                    .l1Ttl(Duration.ofSeconds(10))
                    .l2Ttl(Duration.ofMinutes(30))
                    .redisKeyPrefix("test:")
                    .valueType(String.class)
                    .strongConsistency(false)
                    .nullValueCache(true);
            cache = new DefaultMultiLevelCache<>(config, redisUtil, serializer);
            lenient().when(redisUtil.boundValueOps(anyString())).thenReturn(boundValueOperations);
        }

        @Test
        @DisplayName("L1 命中时不访问 Redis")
        void l1HitSkipsRedis() {
            // 预先写入（put 会写 L1 + L2）
            cache.put(100, "cached-value");
            // 清除 Redis 调用记录
            reset(redisUtil);

            String result = cache.getOrLoad(100, k -> "should-not-load");
            assertEquals("cached-value", result);
            // L1 命中，不应该查询 Redis
            verify(redisUtil, never()).boundValueOps(anyString());
        }

        @Test
        @DisplayName("L1 未命中但 L2 命中时从 Redis 获取并回填 L1")
        void l1MissL2Hit() {
            String redisKey = "test:l2-cache:" + 200;
            when(boundValueOperations.get()).thenReturn("redis-value");

            String result = cache.getOrLoad(200, k -> {
                throw new AssertionError("loader should not be called when L2 hits");
            });
            assertEquals("redis-value", result);
            assertEquals(1, cache.stats().getL2HitCount());

            // 确认 L2 命中后回填了 L1（再次查询不再访问 Redis）
            verify(redisUtil, times(1)).boundValueOps(redisKey);
            verify(boundValueOperations, times(1)).get();
        }

        @Test
        @DisplayName("L1 未命中且 L2 也未命中时调用 loader 并回填两级")
        void l1MissL2MissCallsLoader() {
            String redisKey = "test:l2-cache:300";
            when(boundValueOperations.get()).thenReturn(null);

            String result = cache.getOrLoad(300, k -> "loaded-" + k);
            assertEquals("loaded-300", result);
            assertEquals(1, cache.stats().getMissCount());
            assertEquals(1, cache.stats().getLoadCount());
            assertEquals(1, cache.stats().getLoadSuccessCount());

            // 验证写回了 L2
            verify(boundValueOperations).set("loaded-300", Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("L2 返回 NULL_SENTINEL 字符串时命中哨兵，返回 null")
        void l2ReturnsNullSentinel() {
            String redisKey = "test:l2-cache:400";
            // getFromL2 首次读取返回 null → 进入 isL2NullSentinel 二次读取，命中哨兵
            when(boundValueOperations.get()).thenReturn("NULL_SENTINEL");

            String result = cache.getOrLoad(400, k -> {
                throw new AssertionError("loader should not be called");
            });

            assertNull(result);
            assertEquals(1, cache.stats().getL2HitCount());
        }

        @Test
        @DisplayName("put 写入 L1 和 L2")
        void putWritesBothLevels() {
            String redisKey = "test:l2-cache:500";

            cache.put(500, "put-value");

            // L1 hit
            assertEquals("put-value", cache.getOrLoad(500, k -> "fallback"));

            // L2 写入
            verify(boundValueOperations).set("put-value", Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("L2 命中回填 L1 时读取 Redis 剩余 TTL")
        void l2HitReadsRedisRemainingTtlBeforeRefillL1() {
            String redisKey = "test:l2-cache:250";
            when(boundValueOperations.get()).thenReturn("redis-ttl-value");
            when(redisUtil.getExpire(redisKey, TimeUnit.MILLISECONDS)).thenReturn(1_000L);

            assertEquals("redis-ttl-value", cache.getOrLoad(250, k -> "fallback"));

            verify(redisUtil).getExpire(redisKey, TimeUnit.MILLISECONDS);
        }

        @Test
        @DisplayName("L2 值类型不匹配时忽略缓存并回源")
        void l2TypeMismatchFallsBackToLoader() {
            String redisKey = "test:l2-cache:260";
            when(boundValueOperations.get()).thenReturn(123);

            String result = cache.getOrLoad(260, k -> "loaded-after-type-mismatch");

            assertEquals("loaded-after-type-mismatch", result);
            assertEquals(1, cache.stats().getMissCount());
            verify(boundValueOperations).set("loaded-after-type-mismatch", Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("L2 泛型集合元素反序列化为 Map 时忽略缓存并回源")
        void l2CollectionWithRawJsonElementsFallsBackToLoader() {
            @SuppressWarnings("unchecked")
            RedisUtil redisUtil = mock(RedisUtil.class);
            CacheConfig<Integer, List<String>> config = CacheConfig.<Integer, List<String>>builder("list-cache")
                    .l1MaxSize(100)
                    .l1Ttl(Duration.ofSeconds(10))
                    .l2Ttl(Duration.ofMinutes(30))
                    .redisKeyPrefix("test:")
                    .valueType(List.class)
                    .strongConsistency(false)
                    .nullValueCache(true);
            DefaultMultiLevelCache<Integer, List<String>> listCache =
                    new DefaultMultiLevelCache<>(config, redisUtil, serializer);
            String redisKey = "test:list-cache:270";
            when(redisUtil.boundValueOps(redisKey)).thenReturn(boundValueOperations);
            when(boundValueOperations.get()).thenReturn(List.of(Map.of("name", "raw")));

            List<String> result = listCache.getOrLoad(270, key -> List.of("loaded"));

            assertEquals(List.of("loaded"), result);
            verify(boundValueOperations).set(List.of("loaded"), Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("put 指定 TTL 写入 L2 使用指定 TTL")
        void putWithCustomTtlWritesL2() {
            String redisKey = "test:l2-cache:600";
            Duration customTtl = Duration.ofHours(1);

            cache.put(600, "custom", customTtl);

            verify(boundValueOperations).set("custom", customTtl);
        }

        @Test
        @DisplayName("evict 删除 L1 和 L2")
        void evictRemovesBothLevels() {
            String redisKey = "test:l2-cache:700";
            cache.put(700, "pre");

            cache.evict(700);

            // L1 删除后，查询走 L2（返回 null 说明 L2 也被清理）
            verify(redisUtil).delete(redisKey);
            assertEquals(1, cache.stats().getEvictionCount());
        }

        @Test
        @DisplayName("loader 返回 null + nullValueCache=true 时写 NULL_SENTINEL 到 L2")
        void nullValueToL2Sentinel() {
            String redisKey = "test:l2-cache:800";
            when(boundValueOperations.get()).thenReturn(null);

            String result = cache.getOrLoad(800, k -> null);
            assertNull(result);

            // 验证写入了 NULL_SENTINEL 到 L2
            verify(boundValueOperations).set("NULL_SENTINEL", Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("L2 Redis 异常时自动降级——后续查询只走 L1")
        void l2DegradationOnException() {
            String redisKey = "test:l2-cache:900";
            when(boundValueOperations.get()).thenThrow(new RuntimeException("Redis connection refused"));

            // 即使 Redis 异常，loader 正常返回结果
            String result = cache.getOrLoad(900, k -> "degraded-loaded");
            assertEquals("degraded-loaded", result);
            assertEquals(1, cache.stats().getL2DegradedCount());

            // 降级后，L2 不再被访问——再次查询命中 L1
            String result2 = cache.getOrLoad(900, k -> "fallback");
            assertEquals("degraded-loaded", result2);

            // Redis get 只被调用了 1 次（第一次尝试），之后因降级不再调用
            verify(boundValueOperations, times(1)).get();
        }

        @Test
        @DisplayName("L1 命中时 stats 正确记录 L1 命中")
        void statsL1HitWithRedis() {
            cache.put(1, "s1");
            cache.put(2, "s2");
            // reset to clear put side effects
            reset(redisUtil);

            cache.getOrLoad(1, k -> "x");
            cache.getOrLoad(2, k -> "x");
            cache.getOrLoad(1, k -> "x");

            assertEquals(3, cache.stats().getL1HitCount());
            assertEquals(0, cache.stats().getL2HitCount());
            assertEquals(0, cache.stats().getMissCount());
        }
    }

    // ================================================================
    // CacheTemplate 测试
    // ================================================================

    @Nested
    @DisplayName("CacheTemplate 模板方法")
    class CacheTemplateTests {

        private MultiLevelCache<String, String> cache;

        @BeforeEach
        void setUp() {
            CacheConfig<String, String> config = CacheConfig.<String, String>builder("template");
            cache = new DefaultMultiLevelCache<>(config, null, new SimpleCacheSerializer());
        }

        @Test
        @DisplayName("getOrLoad 静态方法委托给 Cache 实例")
        void getOrLoadDelegation() {
            cache.put("k1", "v1");
            String result = CacheTemplate.getOrLoad(cache, "k1", k -> "fallback");
            assertEquals("v1", result);
        }

        @Test
        @DisplayName("put 静态方法委托给 Cache 实例")
        void putDelegation() {
            CacheTemplate.put(cache, "k2", "static-put");
            assertEquals("static-put", cache.getOrLoad("k2", k -> "fallback"));
        }

        @Test
        @DisplayName("put 带 TTL 静态方法")
        void putWithTtlDelegation() {
            CacheTemplate.put(cache, "k3", "ttl-value", Duration.ofMinutes(5));
            assertEquals("ttl-value", cache.getOrLoad("k3", k -> "fallback"));
        }

        @Test
        @DisplayName("evict 静态方法委托给 Cache 实例")
        void evictDelegation() {
            cache.put("k4", "to-evict");
            CacheTemplate.evict(cache, "k4");
            String result = cache.getOrLoad("k4", k -> "reloaded");
            assertEquals("reloaded", result);
        }
    }

    // ================================================================
    // CacheMonitor 测试
    // ================================================================

    @Nested
    @DisplayName("CacheMonitor 监控快照")
    class CacheMonitorTests {

        private CacheManager cacheManager;
        private CacheMonitor monitor;

        @BeforeEach
        void setUp() {
            cacheManager = new CacheManager(null, new SimpleCacheSerializer());
            monitor = new CacheMonitor(cacheManager);
        }

        @Test
        @DisplayName("空缓存时 snapshot 返回空 Map")
        void emptySnapshot() {
            Map<String, CacheStats> snapshot = monitor.snapshot();
            assertTrue(snapshot.isEmpty());
        }

        @Test
        @DisplayName("有缓存时 snapshot 包含所有实例统计")
        void snapshotWithCaches() {
            MultiLevelCache<String, String> c1 = cacheManager.getOrCreate(CacheConfig.<String, String>builder("c1"));
            MultiLevelCache<Integer, String> c2 = cacheManager.getOrCreate(CacheConfig.<Integer, String>builder("c2"));

            c1.put("k", "v");
            c2.put(1, "v");

            Map<String, CacheStats> snapshot = monitor.snapshot();
            assertEquals(2, snapshot.size());
            assertNotNull(snapshot.get("c1"));
            assertNotNull(snapshot.get("c2"));
        }

        @Test
        @DisplayName("logStats 记录日志不抛异常")
        void logStatsDoesNotThrow() {
            cacheManager.getOrCreate(CacheConfig.builder("log-test"));
            assertDoesNotThrow(() -> monitor.logStats());
        }
    }

    // ================================================================
    // 端到端集成场景测试
    // ================================================================

    @Nested
    @DisplayName("端到端集成场景")
    class IntegrationScenarios {

        private static final SimpleCacheSerializer serializer = new SimpleCacheSerializer();

        // ---- 场景 1: CacheManager + 多实例 ----
        @Test
        @DisplayName("场景：CacheManager 管理多个独立缓存")
        void scenarioMultiCacheManager() {
            CacheManager mgr = new CacheManager(null, serializer);

            MultiLevelCache<String, String> userCache = mgr.getOrCreate(
                    CacheConfig.<String, String>builder("user").l1MaxSize(10));
            MultiLevelCache<Long, TestValue> orderCache = mgr.getOrCreate(
                    CacheConfig.<Long, TestValue>builder("order").l1MaxSize(5));

            // 写入用户
            userCache.put("u1", "Alice");
            userCache.put("u2", "Bob");

            // 写入订单
            orderCache.put(1L, new TestValue("order-1"));
            orderCache.put(2L, new TestValue("order-2"));

            // 读取验证
            assertEquals("Alice", userCache.getOrLoad("u1", k -> "fallback"));
            assertEquals("Bob", userCache.getOrLoad("u2", k -> "fallback"));
            assertEquals(new TestValue("order-2"), orderCache.getOrLoad(2L, k -> new TestValue("fb")));
            assertNotEquals(userCache.stats().getTotalRequests(), orderCache.stats().getTotalRequests());
        }

        // ---- 场景 2: 读穿模式（Cache-Aside）- ---
        @Test
        @DisplayName("场景：模拟数据库回源——读穿模式")
        void scenarioReadThrough() {
            MultiLevelCache<String, TestValue> cache = new DefaultMultiLevelCache<>(
                    CacheConfig.<String, TestValue>builder("readthrough"),
                    null, serializer);

            // 模拟数据库
            java.util.Map<String, TestValue> fakeDb = new java.util.HashMap<>();
            fakeDb.put("u:1", new TestValue("db-alice"));
            fakeDb.put("u:2", new TestValue("db-bob"));

            AtomicInteger dbCalls = new AtomicInteger(0);
            Function<String, TestValue> countedLoader = key -> {
                dbCalls.incrementAndGet();
                return fakeDb.get(key);
            };

            // 首次：穿透到 DB
            assertEquals(new TestValue("db-alice"), cache.getOrLoad("u:1", countedLoader));
            assertEquals(1, dbCalls.get());

            // 第二次：命中缓存
            assertEquals(new TestValue("db-alice"), cache.getOrLoad("u:1", countedLoader));
            assertEquals(1, dbCalls.get()); // 未增加
        }

        // ---- 场景 3: 防穿透 ----
        @Test
        @DisplayName("场景：缓存穿透防护——不存在的 key 缓存哨兵")
        void scenarioPenetrationProtection() {
            MultiLevelCache<String, TestValue> cache = new DefaultMultiLevelCache<>(
                    CacheConfig.<String, TestValue>builder("penetration")
                            .nullValueCache(true)
                            .nullValueTtl(Duration.ofSeconds(5)),
                    null, serializer);

            AtomicInteger dbCalls = new AtomicInteger(0);
            Function<String, TestValue> loader = key -> {
                dbCalls.incrementAndGet();
                return null; // DB 中不存在
            };

            assertNull(cache.getOrLoad("ghost", loader));
            assertEquals(1, dbCalls.get());

            // 连续查询 10 次，只穿透 1 次
            for (int i = 0; i < 10; i++) {
                assertNull(cache.getOrLoad("ghost", loader));
            }
            assertEquals(1, dbCalls.get());
        }
    }
}
