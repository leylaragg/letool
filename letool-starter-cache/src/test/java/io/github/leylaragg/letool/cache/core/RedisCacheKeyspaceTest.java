package io.github.leylaragg.letool.cache.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        String dataKey = keyspace.clusteredKey("project:1");
        String versionKey = keyspace.versionKey("project:1");
        String fenceKey = keyspace.fenceKey("project:1");

        assertTrue(dataKey.startsWith("app:cache:ruleIndex:"));
        assertTrue(dataKey.endsWith(":project:1"));
        assertTrue(versionKey.startsWith("app:cache:%META%:ruleIndex:"));
        assertTrue(versionKey.endsWith(":version"));
        assertEquals(hashTag(dataKey), hashTag(versionKey));
        assertEquals(hashTag(dataKey), hashTag(fenceKey));
        assertNotEquals(hashTag(dataKey), hashTag(keyspace.clusteredKey("project:2")));
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
        when(keyCommands.unlink(any(byte[].class), any(byte[].class))).thenReturn(2L);
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

    /** 业务前缀中的 glob 字符必须按字面量匹配，不能扩大扫描范围。 */
    @Test
    void shouldEscapeBusinessPrefixForScan() {
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
        when(cursor.hasNext()).thenReturn(false);
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        keyspace.scanAndUnlink(redisTemplate, "项目:*?[草稿]");

        ArgumentCaptor<ScanOptions> optionsCaptor = ArgumentCaptor.forClass(ScanOptions.class);
        verify(keyCommands).scan(optionsCaptor.capture());
        assertTrue(matches(
                optionsCaptor.getValue(),
                keySerializer,
                "app:cache:ruleIndex:项目:\\*\\?\\[草稿\\]*"
        ));
        verify(redisTemplate, never()).keys(any());
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
        RedisClusterNode secondMasterNode = mock(RedisClusterNode.class);
        RedisClusterNode replicaNode = mock(RedisClusterNode.class);
        StringRedisSerializer keySerializer = StringRedisSerializer.UTF_8;
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        @SuppressWarnings("unchecked")
        Cursor<byte[]> secondCursor = mock(Cursor.class);
        doReturn(keySerializer).when(redisTemplate).getKeySerializer();
        executeUsing(redisTemplate, clusterConnection);
        when(clusterConnection.keyCommands()).thenReturn(keyCommands);
        when(masterNode.isMaster()).thenReturn(true);
        when(masterNode.isConnected()).thenReturn(true);
        when(masterNode.getSlotRange()).thenReturn(new RedisClusterNode.SlotRange(0, 8191));
        when(secondMasterNode.isMaster()).thenReturn(true);
        when(secondMasterNode.isConnected()).thenReturn(true);
        when(secondMasterNode.getSlotRange()).thenReturn(new RedisClusterNode.SlotRange(8192, 16383));
        when(replicaNode.isMaster()).thenReturn(false);
        when(replicaNode.isConnected()).thenReturn(true);
        when(clusterConnection.clusterGetNodes()).thenReturn(List.of(
                masterNode,
                secondMasterNode,
                replicaNode
        ));
        when(clusterConnection.ping(masterNode)).thenReturn("PONG");
        when(clusterConnection.ping(secondMasterNode)).thenReturn("pong");
        when(clusterConnection.scan(eq(masterNode), any(ScanOptions.class))).thenReturn(cursor);
        when(clusterConnection.scan(eq(secondMasterNode), any(ScanOptions.class))).thenReturn(secondCursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(keySerializer.serialize("app:cache:ruleIndex:project:1"));
        when(secondCursor.hasNext()).thenReturn(true, false);
        when(secondCursor.next()).thenReturn(keySerializer.serialize("app:cache:ruleIndex:project:2"));
        when(keyCommands.unlink(any(byte[].class))).thenReturn(1L);
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        long removed = keyspace.scanAndUnlink(redisTemplate);

        assertEquals(2L, removed);
        verify(clusterConnection).scan(eq(masterNode), any(ScanOptions.class));
        verify(clusterConnection).scan(eq(secondMasterNode), any(ScanOptions.class));
        verify(clusterConnection, never()).scan(eq(replicaNode), any(ScanOptions.class));
        verify(keyCommands).unlink(eq(keySerializer.serialize("app:cache:ruleIndex:project:1")));
        verify(keyCommands).unlink(eq(keySerializer.serialize("app:cache:ruleIndex:project:2")));
        verify(cursor).close();
        verify(secondCursor).close();
    }

    /** 应用客户端无法访问任一主节点时，必须在所有节点扫描前原样传播探测异常。 */
    @Test
    void shouldPropagateClusterMasterPingFailureBeforeScanning() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisClusterConnection clusterConnection = mock(RedisClusterConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        RedisClusterNode firstMaster = masterNode(
                7000,
                new RedisClusterNode.SlotRange(0, 8191)
        );
        RedisClusterNode secondMaster = masterNode(
                7001,
                new RedisClusterNode.SlotRange(8192, 16383)
        );
        RuntimeException pingFailure = new RuntimeException("second master ping failed");
        doReturn(StringRedisSerializer.UTF_8).when(redisTemplate).getKeySerializer();
        executeUsing(redisTemplate, clusterConnection);
        when(clusterConnection.clusterGetNodes()).thenReturn(List.of(firstMaster, secondMaster));
        when(clusterConnection.keyCommands()).thenReturn(keyCommands);
        when(clusterConnection.ping(firstMaster)).thenReturn("PONG");
        when(clusterConnection.ping(secondMaster)).thenThrow(pingFailure);
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> keyspace.scanAndUnlink(redisTemplate)
        );

        assertSame(pingFailure, actual);
        verify(clusterConnection).ping(firstMaster);
        verify(clusterConnection).ping(secondMaster);
        verify(clusterConnection, never()).scan(
                any(RedisClusterNode.class),
                any(ScanOptions.class)
        );
        verifyNoInteractions(keyCommands);
    }

    /** 主节点 PING 未返回 PONG 时，必须在任何扫描或删除前失败。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "OK")
    void shouldRejectUnexpectedClusterMasterPingResponseBeforeScanning(String pingResponse) {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisClusterConnection clusterConnection = mock(RedisClusterConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        RedisClusterNode firstMaster = masterNode(
                7000,
                new RedisClusterNode.SlotRange(0, 8191)
        );
        RedisClusterNode secondMaster = masterNode(
                7001,
                new RedisClusterNode.SlotRange(8192, 16383)
        );
        doReturn(StringRedisSerializer.UTF_8).when(redisTemplate).getKeySerializer();
        executeUsing(redisTemplate, clusterConnection);
        when(clusterConnection.clusterGetNodes()).thenReturn(List.of(firstMaster, secondMaster));
        when(clusterConnection.keyCommands()).thenReturn(keyCommands);
        when(clusterConnection.ping(firstMaster)).thenReturn("PONG");
        when(clusterConnection.ping(secondMaster)).thenReturn(pingResponse);
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        assertThrows(IllegalStateException.class, () -> keyspace.scanAndUnlink(redisTemplate));

        verify(clusterConnection, never()).scan(
                any(RedisClusterNode.class),
                any(ScanOptions.class)
        );
        verifyNoInteractions(keyCommands);
    }

    /**
     * 任一主节点不可用时必须在扫描前失败，避免只删除部分槽中的缓存数据。
     */
    @Test
    void shouldRejectUnavailableClusterMasterBeforeScanning() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisClusterConnection clusterConnection = mock(RedisClusterConnection.class);
        RedisClusterNode availableMaster = mock(RedisClusterNode.class);
        RedisClusterNode unavailableMaster = mock(RedisClusterNode.class);
        StringRedisSerializer keySerializer = StringRedisSerializer.UTF_8;
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        doReturn(keySerializer).when(redisTemplate).getKeySerializer();
        executeUsing(redisTemplate, clusterConnection);
        when(availableMaster.isMaster()).thenReturn(true);
        when(availableMaster.isConnected()).thenReturn(true);
        when(availableMaster.getSlotRange()).thenReturn(new RedisClusterNode.SlotRange(0, 8191));
        when(unavailableMaster.isMaster()).thenReturn(true);
        when(unavailableMaster.isConnected()).thenReturn(false);
        when(unavailableMaster.getSlotRange()).thenReturn(new RedisClusterNode.SlotRange(8192, 16383));
        when(clusterConnection.clusterGetNodes()).thenReturn(List.of(
                availableMaster,
                unavailableMaster
        ));
        when(clusterConnection.scan(eq(availableMaster), any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        assertThrows(IllegalStateException.class, () -> keyspace.scanAndUnlink(redisTemplate));

        verify(clusterConnection, never()).scan(
                any(RedisClusterNode.class),
                any(ScanOptions.class)
        );
    }

    /** 未知角色节点处于握手或无地址状态时，当前拓扑不能用于区域清理。 */
    @ParameterizedTest
    @EnumSource(
            value = RedisClusterNode.Flag.class,
            names = {"HANDSHAKE", "NOADDR"}
    )
    void shouldRejectUntrustedUnknownClusterNodeBeforeScanning(RedisClusterNode.Flag flag) {
        RedisClusterNode firstMaster = masterNode(
                7000,
                new RedisClusterNode.SlotRange(0, 8191)
        );
        RedisClusterNode secondMaster = masterNode(
                7001,
                new RedisClusterNode.SlotRange(8192, 16383)
        );
        RedisClusterNode unknownNode = RedisClusterNode.newRedisClusterNode()
                .listeningAt("127.0.0.1", 7002)
                .withFlags(Set.of(flag))
                .linkState(RedisClusterNode.LinkState.CONNECTED)
                .build();

        assertClusterNodesRejectedBeforeScanning(List.of(
                firstMaster,
                secondMaster,
                unknownNode
        ));
    }

    /** FAIL 与 PFAIL 主节点都必须在扫描前使清理失败。 */
    @ParameterizedTest
    @EnumSource(
            value = RedisClusterNode.Flag.class,
            names = {"FAIL", "PFAIL"}
    )
    void shouldRejectFailedClusterMasterBeforeScanning(RedisClusterNode.Flag flag) {
        assertClusterNodesRejectedBeforeScanning(List.of(
                masterNode(7000, new RedisClusterNode.SlotRange(0, 8191)),
                masterNode(7001, new RedisClusterNode.SlotRange(8192, 16383), flag)
        ));
    }

    /** 所有主节点在线但 Slot 未完整覆盖时，也必须在扫描前失败。 */
    @Test
    void shouldRejectClusterSlotGapBeforeScanning() {
        assertInvalidClusterSlotRanges(
                new RedisClusterNode.SlotRange(0, 8191),
                new RedisClusterNode.SlotRange(8193, 16383)
        );
    }

    /** Slot 完整但被多个主节点重复声明时，也不能开始局部清理。 */
    @Test
    void shouldRejectOverlappingClusterSlotsBeforeScanning() {
        assertInvalidClusterSlotRanges(
                new RedisClusterNode.SlotRange(0, 8192),
                new RedisClusterNode.SlotRange(8192, 16383)
        );
    }

    /** UNLINK 未返回结果时不能把已提交批次计为完整删除。 */
    @Test
    void shouldRejectMissingUnlinkResult() {
        assertThrows(IllegalStateException.class, () -> scanWithUnlinkResult(null));
    }

    /** UNLINK 返回负数时视为命令结果违反接口不变量。 */
    @Test
    void shouldRejectNegativeUnlinkResult() {
        assertThrows(IllegalStateException.class, () -> scanWithUnlinkResult(-1L));
    }

    /** SCAN 后全部 Key 并发消失时，UNLINK 返回零是合法结果。 */
    @Test
    void shouldAcceptZeroUnlinkResult() {
        assertEquals(0L, scanWithUnlinkResult(0L));
    }

    /** SCAN 后 Key 并发消失时，按 UNLINK 实际确认数量返回。 */
    @Test
    void shouldAcceptConcurrentMissingKeyAndReturnActualUnlinkCount() {
        assertEquals(1L, scanWithUnlinkResult(1L));
    }

    /** UNLINK 返回数量超过请求批次时视为命令结果违反接口不变量。 */
    @Test
    void shouldRejectExcessiveUnlinkResult() {
        assertThrows(IllegalStateException.class, () -> scanWithUnlinkResult(3L));
    }

    /** 集群节点扫描异常必须原样传播，后续节点和删除命令都不能继续执行。 */
    @Test
    void shouldPropagateClusterScanFailure() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisClusterConnection clusterConnection = mock(RedisClusterConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        RedisClusterNode firstMaster = masterNode(
                7000,
                new RedisClusterNode.SlotRange(0, 8191)
        );
        RedisClusterNode secondMaster = masterNode(
                7001,
                new RedisClusterNode.SlotRange(8192, 16383)
        );
        RuntimeException scanFailure = new RuntimeException("scan failed");
        doReturn(StringRedisSerializer.UTF_8).when(redisTemplate).getKeySerializer();
        executeUsing(redisTemplate, clusterConnection);
        when(clusterConnection.clusterGetNodes()).thenReturn(List.of(firstMaster, secondMaster));
        when(clusterConnection.ping(firstMaster)).thenReturn("PONG");
        when(clusterConnection.ping(secondMaster)).thenReturn("PONG");
        when(clusterConnection.keyCommands()).thenReturn(keyCommands);
        when(clusterConnection.scan(eq(firstMaster), any(ScanOptions.class)))
                .thenThrow(scanFailure);
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> keyspace.scanAndUnlink(redisTemplate)
        );

        assertSame(scanFailure, actual);
        verify(clusterConnection, never()).scan(eq(secondMaster), any(ScanOptions.class));
        verifyNoInteractions(keyCommands);
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
     * 提取 Redis Cluster 用于计算 Slot 的 Hash Tag。
     *
     * @param key Redis Key
     * @return 大括号内的 Hash Tag
     */
    private static String hashTag(String key) {
        int start = key.indexOf('{');
        int end = key.indexOf('}', start + 1);
        assertTrue(start >= 0 && end > start + 1, "Redis Key 必须包含非空 Hash Tag");
        return key.substring(start + 1, end);
    }

    /**
     * 创建状态正常的 Redis Cluster 主节点。
     *
     * @param port 测试节点端口
     * @param slotRange 节点负责的 Slot
     * @param extraFlags 除 MASTER 外的附加状态
     * @return 具有真实状态判断行为的主节点
     */
    private static RedisClusterNode masterNode(
            int port,
            RedisClusterNode.SlotRange slotRange,
            RedisClusterNode.Flag... extraFlags) {
        EnumSet<RedisClusterNode.Flag> flags = EnumSet.of(RedisClusterNode.Flag.MASTER);
        flags.addAll(List.of(extraFlags));
        return RedisClusterNode.newRedisClusterNode()
                .listeningAt("127.0.0.1", port)
                .promotedAs(RedisNode.NodeType.MASTER)
                .withFlags(flags)
                .serving(slotRange)
                .linkState(RedisClusterNode.LinkState.CONNECTED)
                .build();
    }

    /**
     * 验证不可信集群节点会在任何扫描前被拒绝。
     *
     * @param clusterNodes 当前连接发现的完整节点列表
     */
    private static void assertClusterNodesRejectedBeforeScanning(
            List<RedisClusterNode> clusterNodes) {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisClusterConnection clusterConnection = mock(RedisClusterConnection.class);
        StringRedisSerializer keySerializer = StringRedisSerializer.UTF_8;
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        doReturn(keySerializer).when(redisTemplate).getKeySerializer();
        executeUsing(redisTemplate, clusterConnection);
        when(clusterConnection.clusterGetNodes()).thenReturn(clusterNodes);
        when(clusterConnection.scan(any(RedisClusterNode.class), any(ScanOptions.class)))
                .thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        assertThrows(IllegalStateException.class, () -> keyspace.scanAndUnlink(redisTemplate));

        verify(clusterConnection, never()).scan(
                any(RedisClusterNode.class),
                any(ScanOptions.class)
        );
    }

    /**
     * 验证非法 Slot 拓扑在任何节点扫描前被拒绝。
     *
     * @param firstRange 第一个主节点声明的 Slot
     * @param secondRange 第二个主节点声明的 Slot
     */
    private static void assertInvalidClusterSlotRanges(
            RedisClusterNode.SlotRange firstRange,
            RedisClusterNode.SlotRange secondRange) {
        assertClusterNodesRejectedBeforeScanning(List.of(
                masterNode(7000, firstRange),
                masterNode(7001, secondRange)
        ));
    }

    /**
     * 使用指定的 UNLINK 返回值执行区域清理。
     *
     * @param unlinkResult 模拟的 UNLINK 删除数量，{@code null} 表示命令没有返回结果
     * @return 区域清理确认删除的 Key 数量
     */
    private static long scanWithUnlinkResult(Long unlinkResult) {
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
        when(keyCommands.unlink(any(byte[].class), any(byte[].class))).thenReturn(unlinkResult);
        RedisCacheKeyspace keyspace = new RedisCacheKeyspace("app:cache:", "ruleIndex");

        return keyspace.scanAndUnlink(redisTemplate);
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
