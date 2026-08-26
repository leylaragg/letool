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
import org.springframework.data.redis.core.BoundListOperations;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                .redisKeyPrefix("test:cache:")
                .strongConsistency(false)
                .build();
        lenient().when(boundListOperations.leftPush(any())).thenReturn(1L);
        lenient().when(boundListOperations.rightPush(any())).thenReturn(1L);
        lenient().when(redisUtil.expire(any(), anyLong(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("严格写策略下 push 失败必须保留 L1，后续 pop 也不得本地伪成功")
    void strictPushAndPopShouldExposeRedisFailureWithoutMutatingLocalList() {
        String redisKey = "test:cache:events:user:strict";
        when(redisUtil.boundListOps(redisKey)).thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1)).thenReturn(List.of("old"));
        MultiLevelListCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateListCache(strictWriteConfig(), Function.identity(), String.class);
        assertEquals(List.of("old"), cache.range("user:strict", 0, -1));
        RuntimeException redisFailure = new RuntimeException("rpush failed");
        when(boundListOperations.rightPush("new")).thenThrow(redisFailure);

        CacheException pushFailure = assertThrows(CacheException.class,
                () -> cache.rightPush("user:strict", "new"));
        CacheException popFailure = assertThrows(CacheException.class,
                () -> cache.leftPop("user:strict"));

        assertSame(redisFailure, pushFailure.getCause());
        assertSame(redisFailure, popFailure.getCause());
        assertEquals(List.of("old"), cache.range("user:strict", 0, -1));
    }

    @Test
    @DisplayName("严格写策略下 push 的 TTL 失败必须暴露")
    void strictPushShouldRejectFailedTtl() {
        String redisKey = "test:cache:events:user:strict-ttl";
        when(redisUtil.boundListOps(redisKey)).thenReturn(boundListOperations);
        when(redisUtil.expire(redisKey, Duration.ofHours(1).toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(false);
        MultiLevelListCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateListCache(strictWriteConfig(), Function.identity(), String.class);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.rightPush("user:strict-ttl", "new"));

        assertEquals("CACHE_006", thrown.getCode());
        assertTrue(cache.range("user:strict-ttl", 0, -1).isEmpty());
    }

    @Test
    @DisplayName("严格写策略下 removeKey 失败必须保留 L1")
    void strictRemoveKeyShouldExposeFailureWithoutClearingLocalList() {
        String redisKey = "test:cache:events:user:strict-delete";
        when(redisUtil.boundListOps(redisKey)).thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1)).thenReturn(List.of("old"));
        MultiLevelListCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateListCache(strictWriteConfig(), Function.identity(), String.class);
        assertEquals(List.of("old"), cache.range("user:strict-delete", 0, -1));
        RuntimeException redisFailure = new RuntimeException("del failed");
        doThrow(redisFailure).when(redisUtil).delete(redisKey);

        CacheException thrown = assertThrows(CacheException.class,
                () -> cache.removeKey("user:strict-delete"));

        assertSame(redisFailure, thrown.getCause());
        assertEquals(List.of("old"), cache.range("user:strict-delete", 0, -1));
    }

    @Test
    @DisplayName("rightPush 写入 L1 和 Redis List")
    void rightPushWritesLocalAndRedisList() {
        when(redisUtil.boundListOps("test:cache:events:user:1")).thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1))
                .thenReturn(List.of("login"));
        MultiLevelListCache<String, String> cache = new CacheManager(redisUtil, serializer)
                .getOrCreateListCache(config, Function.identity(), String.class);

        cache.rightPush("user:1", "login");

        assertEquals(List.of("login"), cache.range("user:1", 0, -1));
        verify(boundListOperations).rightPush("login");
        verify(redisUtil).expire("test:cache:events:user:1", Duration.ofHours(1).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
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
        when(redisUtil.boundListOps("test:cache:events:user:2")).thenReturn(boundListOperations);
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
        when(redisUtil.boundListOps("test:cache:events:user:3")).thenReturn(boundListOperations);
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
        when(redisUtil.boundListOps("test:cache:events:user:4")).thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1)).thenThrow(new RuntimeException("redis down"));
        CacheManager manager = new CacheManager(redisUtil, serializer);
        MultiLevelListCache<String, String> cache = manager.getOrCreateListCache(config, Function.identity(), String.class);

        assertTrue(cache.range("user:4", 0, -1).isEmpty());

        assertTrue(cache.stats().l2Degraded());
        assertEquals(1, manager.degradedCacheCount());
    }

    @Test
    @DisplayName("局部推入不能让 L1 冒充完整 Redis List 快照")
    void partialPushShouldNotHideExistingRedisElements() {
        when(redisUtil.boundListOps("test:cache:events:user:5"))
                .thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1))
                .thenReturn(List.of("existing", "new"));
        MultiLevelListCache<String, String> cache =
                new CacheManager(redisUtil, serializer)
                        .getOrCreateListCache(
                                config,
                                Function.identity(),
                                String.class
                        );

        cache.rightPush("user:5", "new");

        assertEquals(
                List.of("existing", "new"),
                cache.range("user:5", 0, -1)
        );
        verify(boundListOperations).range(0, -1);
    }

    @Test
    @DisplayName("强一致读取应把 Redis 空列表视为权威结果")
    void strongConsistencyShouldNotReturnStaleListWhenRedisIsEmpty() {
        CacheConfig<String, String> strongConfig =
                CacheConfig.<String, String>builder("events")
                        .l1Ttl(Duration.ofMinutes(10))
                        .l2Ttl(Duration.ofHours(1))
                        .redisKeyPrefix("test:cache:")
                        .strongConsistency(true)
                        .build();
        when(redisUtil.boundListOps("test:cache:events:user:6"))
                .thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1))
                .thenReturn(List.of("old"), List.of());
        MultiLevelListCache<String, String> cache =
                new CacheManager(redisUtil, serializer)
                        .getOrCreateListCache(
                                strongConfig,
                                Function.identity(),
                                String.class
                        );

        assertEquals(List.of("old"), cache.range("user:6", 0, -1));
        assertTrue(cache.range("user:6", 0, -1).isEmpty());
    }

    @Test
    @DisplayName("L1 列表负索引范围应与 Redis LRANGE 一致")
    void localRangeShouldSupportRedisNegativeIndexes() {
        MultiLevelListCache<String, String> cache =
                new CacheManager(null, serializer)
                        .getOrCreateListCache(
                                config,
                                Function.identity(),
                                String.class
                        );
        cache.rightPush("user:7", "a");
        cache.rightPush("user:7", "b");
        cache.rightPush("user:7", "c");
        cache.rightPush("user:7", "d");

        assertEquals(
                List.of("b", "c"),
                cache.range("user:7", -3, -2)
        );
    }

    @Test
    @DisplayName("默认 List 工厂应使用配置的元素类型")
    void defaultFactoryShouldUseConfiguredElementType() {
        CacheConfig<String, String> typedConfig =
                CacheConfig.<String, String>builder("typed-list")
                        .redisKeyPrefix("test:cache:")
                        .strongConsistency(false)
                        .valueType(String.class)
                        .build();
        when(redisUtil.boundListOps("test:cache:typed-list:key"))
                .thenReturn(boundListOperations);
        when(boundListOperations.range(0, -1)).thenReturn(List.of(42));
        MultiLevelListCache<String, String> cache =
                new CacheManager(redisUtil, serializer)
                        .getOrCreateListCache(typedConfig);

        assertTrue(cache.range("key", 0, -1).isEmpty());
    }

    /**
     * 验证区域清理会删除本地列表快照并广播全区域失效消息。
     */
    @Test
    @DisplayName("evictAll 清理 List 区域并广播失效")
    void evictAllShouldClearLocalListAndPublishInvalidation() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        MultiLevelListCache<String, String> cache = new CacheManager(
                null,
                serializer,
                true,
                false,
                "test:cache:",
                publisher
        ).getOrCreateListCache(config, Function.identity(), String.class);
        cache.rightPush("user:9", "login");

        cache.evictAll();

        assertTrue(cache.range("user:9", 0, -1).isEmpty());
        verify(publisher).publish(argThat(message ->
                message.isAll() && "events".equals(message.getCacheName())));
    }

    /** Redis 区域清理失败时不得向其它节点广播不真实的全区域失效。 */
    @Test
    @DisplayName("evictAll 清理 Redis 失败时不广播 ALL")
    void evictAllShouldNotPublishWhenRedisCleanupFails() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        RuntimeException cleanupFailure = new RuntimeException("scan failed");
        when(redisUtil.getTemplate()).thenThrow(cleanupFailure);
        MultiLevelListCache<String, String> cache = new CacheManager(
                redisUtil,
                serializer,
                true,
                true,
                "test:cache:",
                publisher
        ).getOrCreateListCache(config, Function.identity(), String.class);

        CacheException exception = assertThrows(CacheException.class, cache::evictAll);

        assertEquals("CACHE_006", exception.getCode());
        assertSame(cleanupFailure, exception.getCause());
        assertTrue(cache.isL2Degraded());
        verifyNoInteractions(publisher);
    }

    /** 创建只要求 Redis 变更可确认的 List 配置。 */
    private CacheConfig<String, String> strictWriteConfig() {
        return CacheConfig.<String, String>builder("events")
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisKeyPrefix("test:cache:")
                .strongConsistency(false)
                .writeFailurePolicy(CacheWriteFailurePolicy.FAIL_CLOSED)
                .build();
    }
}
