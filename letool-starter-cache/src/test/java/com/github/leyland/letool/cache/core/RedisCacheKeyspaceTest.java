package com.github.leyland.letool.cache.core;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 缓存键空间的关键生产契约测试。
 */
class RedisCacheKeyspaceTest {

    /**
     * 验证缓存名称参与数据 Key 生成，避免不同缓存区域使用相同业务 Key 时串用数据。
     */
    @Test
    void shouldIncludeCacheNameInDataKey() {
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");
        RedisCacheKeyspace nestedKeyspace = new RedisCacheKeyspace("app:cache:", "rule:index");
        RedisCacheKeyspace parentKeyspace = new RedisCacheKeyspace("app:cache:", "rule");

        assertEquals("app:cache:ruleIndex:project:1", keyspace.key("project:1"));
        assertEquals("app:cache:%META%:ruleIndex:version", keyspace.versionKey());
        assertNotEquals(
                nestedKeyspace.key("project:1"),
                parentKeyspace.key("index:project:1")
        );
    }

    /**
     * 验证区域清理使用游标扫描和异步删除，不调用阻塞式 KEYS 或同步批量删除。
     */
    @Test
    void shouldScanAndUnlinkCurrentCacheRegion() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        StringRedisSerializer keySerializer = StringRedisSerializer.UTF_8;
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        doReturn(keySerializer).when(redisTemplate).getKeySerializer();
        executeUsing(redisTemplate, connection);
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                keySerializer.serialize("app:cache:ruleIndex:project:1"),
                keySerializer.serialize("app:cache:ruleIndex:project:2")
        );
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        long removed = keyspace.scanAndUnlink(redisTemplate);

        assertEquals(2L, removed);
        ArgumentCaptor<ScanOptions> optionsCaptor = ArgumentCaptor.forClass(ScanOptions.class);
        verify(keyCommands).scan(optionsCaptor.capture());
        assertTrue(matches(optionsCaptor.getValue(), keySerializer, "app:cache:ruleIndex:*"));
        verify(keyCommands).unlink(
                eq(keySerializer.serialize("app:cache:ruleIndex:project:1")),
                eq(keySerializer.serialize("app:cache:ruleIndex:project:2"))
        );
        verify(redisTemplate, never()).keys("app:cache:ruleIndex:*");
        verify(redisTemplate, never()).delete(List.of(
                "app:cache:ruleIndex:project:1",
                "app:cache:ruleIndex:project:2"
        ));
        verify(cursor).close();
    }

    /**
     * 验证 Redis Cluster 模式会扫描全部可用主节点，并跳过副本节点。
     */
    @Test
    void shouldScanEveryAvailableClusterMaster() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisClusterConnection clusterConnection = mock(RedisClusterConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        RedisClusterNode masterNode = mock(RedisClusterNode.class);
        RedisClusterNode replicaNode = mock(RedisClusterNode.class);
        StringRedisSerializer keySerializer = StringRedisSerializer.UTF_8;
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        doReturn(keySerializer).when(redisTemplate).getKeySerializer();
        executeUsing(redisTemplate, clusterConnection);
        when(clusterConnection.keyCommands()).thenReturn(keyCommands);
        when(masterNode.isMaster()).thenReturn(true);
        when(masterNode.isConnected()).thenReturn(true);
        when(replicaNode.isMaster()).thenReturn(false);
        when(replicaNode.isConnected()).thenReturn(true);
        when(clusterConnection.clusterGetNodes()).thenReturn(List.of(masterNode, replicaNode));
        when(clusterConnection.scan(eq(masterNode), any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(keySerializer.serialize("app:cache:ruleIndex:project:1"));
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        long removed = keyspace.scanAndUnlink(redisTemplate);

        assertEquals(1L, removed);
        verify(clusterConnection).scan(eq(masterNode), any(ScanOptions.class));
        verify(clusterConnection, never()).scan(eq(replicaNode), any(ScanOptions.class));
        verify(keyCommands).unlink(eq(keySerializer.serialize("app:cache:ruleIndex:project:1")));
        verify(cursor).close();
    }

    /**
     * 判断扫描参数是否严格限制在当前缓存区域。
     *
     * @param options Redis 扫描参数
     * @param keySerializer Redis Key 字符串序列化器
     * @param expectedPattern 期望的键匹配模式
     * @return 匹配当前缓存区域时返回 {@code true}
     */
    private static boolean matches(
            ScanOptions options,
            StringRedisSerializer keySerializer,
            String expectedPattern
    ) {
        return java.util.Arrays.equals(keySerializer.serialize(expectedPattern), options.getBytePattern())
                && Long.valueOf(1000L).equals(options.getCount());
    }

    /**
     * 让 RedisTemplate 在测试中使用指定的原生 Redis 连接执行回调。
     *
     * @param redisTemplate 待模拟的 RedisTemplate
     * @param connection Redis 原生连接
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void executeUsing(
            RedisTemplate<String, Object> redisTemplate,
            RedisConnection connection
    ) {
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
    }
}
