package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.tool.util.DigestUtil;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Redis 缓存区域的键生成与批量清理组件。
 *
 * <p>每个缓存实例拥有独立的键空间，最终数据 Key 格式为
 * {@code <全局前缀><缓存名称编码>:<业务Key>}。区域清理使用游标式 SCAN 和异步 UNLINK，
 * 避免在大键空间中执行阻塞式 KEYS 或同步删除大 Value。</p>
 */
final class RedisCacheKeyspace {

    /** SCAN 每次建议 Redis 返回的 Key 数量，也是单次 UNLINK 的最大批次大小。 */
    private static final long SCAN_BATCH_SIZE = 1000L;

    /** 当前缓存区域的完整 Key 前缀，末尾包含分隔符。 */
    private final String regionPrefix;

    /** 当前缓存区域的元数据 Key 前缀，与业务数据扫描范围隔离。 */
    private final String metadataPrefix;

    /** 当前缓存区域的 Redis glob 匹配模式。 */
    private final String scanPattern;

    /**
     * 创建缓存键空间。
     *
     * @param redisKeyPrefix Redis 全局 Key 前缀
     * @param cacheName 缓存区域名称
     */
    RedisCacheKeyspace(String redisKeyPrefix, String cacheName) {
        Objects.requireNonNull(redisKeyPrefix, "Redis Key 前缀不能为空");
        Objects.requireNonNull(cacheName, "缓存名称不能为空");
        String cacheNameSegment = encodeCacheName(cacheName);
        this.regionPrefix = redisKeyPrefix + cacheNameSegment + ":";
        this.metadataPrefix = redisKeyPrefix + "%META%:" + cacheNameSegment + ":";
        this.scanPattern = escapeGlob(regionPrefix) + "*";
    }

    /**
     * 生成当前缓存区域的数据 Key。
     *
     * @param serializedKey 已序列化的业务 Key
     * @return 包含缓存名称的完整 Redis Key
     */
    String key(String serializedKey) {
        return regionPrefix + Objects.requireNonNull(serializedKey, "序列化业务 Key 不能为空");
    }

    /**
     * 生成适用于 Redis Cluster 单 Key 原子脚本的数据 Key。
     *
     * @param serializedKey 已序列化的业务 Key
     * @return 包含业务 Key 独立 Hash Tag 的完整 Redis Key
     */
    String clusteredKey(String serializedKey) {
        String key = Objects.requireNonNull(serializedKey, "序列化业务 Key 不能为空");
        return regionPrefix + hashTag(key) + ":" + key;
    }

    /**
     * 生成当前缓存区域的版本元数据 Key。
     *
     * <p>版本 Key 不属于数据扫描范围，区域清理后继续保持单调递增，防止其它 JVM
     * 因版本号回退而误用旧的 L1 数据。</p>
     *
     * @return 当前缓存区域的版本元数据 Key
     */
    String versionKey(String serializedKey) {
        String key = Objects.requireNonNull(serializedKey, "序列化业务 Key 不能为空");
        return metadataPrefix + hashTag(key) + ":version";
    }

    /**
     * 生成与数据 Key、版本 Key 同槽的写事务围栏 Key。
     *
     * @param serializedKey 已序列化的业务 Key
     * @return 单 Key 围栏元数据 Key
     */
    String fenceKey(String serializedKey) {
        String key = Objects.requireNonNull(serializedKey, "序列化业务 Key 不能为空");
        return metadataPrefix + hashTag(key) + ":fence";
    }

    /**
     * 生成区域级版本 Key，仅用于区域清理后使全部 L1 快照失效。
     *
     * @return 当前缓存区域的区域版本 Key
     */
    String regionVersionKey() {
        return metadataPrefix + "region-version";
    }

    /**
     * 生成兼容现有区域版本校验的元数据 Key。
     *
     * @return 当前缓存区域的旧版区域版本 Key
     */
    String versionKey() {
        return metadataPrefix + "version";
    }

    /**
     * 生成 Redis 连通性探测 Key。
     *
     * @return 不与业务数据冲突的探测 Key
     */
    String healthCheckKey() {
        return metadataPrefix + "health";
    }

    /**
     * 使用 SCAN 和 UNLINK 清理当前缓存区域的全部 Redis Key。
     *
     * <p>游标按批次遍历，不会把全部 Key 一次性加载到 JVM 内存。Redis Cluster 模式下逐个扫描
     * 可用主节点，避免只清理当前连接节点。UNLINK 只解除 Key 与 Value 的关联，实际内存回收由
     * Redis 后台线程完成，适合缓存区域清理。</p>
     *
     * @param redisOperations Redis 原生操作入口
     * @return 提交给 UNLINK 的 Key 数量
     */
    long scanAndUnlink(RedisOperations<String, Object> redisOperations) {
        Objects.requireNonNull(redisOperations, "Redis 操作入口不能为空");
        StringRedisSerializer keySerializer = requireStringKeySerializer(redisOperations);
        ScanOptions options = ScanOptions.scanOptions()
                .match(Objects.requireNonNull(keySerializer.serialize(scanPattern), "Redis 扫描模式序列化结果不能为空"))
                .count(SCAN_BATCH_SIZE)
                .build();
        RedisCallback<Long> callback = connection -> scanAndUnlink(connection, options);
        Long removed = redisOperations.execute(callback);
        return removed == null ? 0L : removed;
    }

    /**
     * 根据 Redis 连接类型清理当前缓存区域。
     *
     * @param connection Redis 原生连接
     * @param options 限定当前缓存区域的扫描参数
     * @return 提交给 UNLINK 的 Key 数量
     */
    private long scanAndUnlink(RedisConnection connection, ScanOptions options) {
        if (!(connection instanceof RedisClusterConnection clusterConnection)) {
            RedisKeyCommands keyCommands = connection.keyCommands();
            return scanCursorAndUnlink(keyCommands.scan(options), keyCommands);
        }

        long removed = 0L;
        int availableMasterCount = 0;
        RedisKeyCommands keyCommands = clusterConnection.keyCommands();
        for (RedisClusterNode node : clusterConnection.clusterGetNodes()) {
            if (!node.isMaster() || !node.isConnected() || node.isMarkedAsFail()) {
                continue;
            }
            availableMasterCount++;
            removed += scanCursorAndUnlink(clusterConnection.scan(node, options), keyCommands);
        }
        if (availableMasterCount == 0) {
            throw new IllegalStateException("Redis Cluster 中没有可用于缓存区域清理的主节点");
        }
        return removed;
    }

    /**
     * 消费单个 Redis 节点的扫描游标，并按固定批次异步删除匹配的 Key。
     *
     * @param cursor Redis Key 扫描游标
     * @param keyCommands 执行 UNLINK 的 Redis Key 命令入口
     * @return 提交给 UNLINK 的 Key 数量
     */
    private long scanCursorAndUnlink(Cursor<byte[]> cursor, RedisKeyCommands keyCommands) {
        List<byte[]> batch = new ArrayList<>((int) SCAN_BATCH_SIZE);
        long removed = 0L;
        try (cursor) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == SCAN_BATCH_SIZE) {
                    keyCommands.unlink(batch.toArray(byte[][]::new));
                    removed += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                keyCommands.unlink(batch.toArray(byte[][]::new));
                removed += batch.size();
            }
        }
        return removed;
    }

    /**
     * 校验 Redis Key 使用字符串序列化器，确保 SCAN 的 MATCH 模式与实际 Key 编码一致。
     *
     * @param redisOperations Redis 原生操作入口
     * @return 当前 RedisTemplate 使用的字符串 Key 序列化器
     */
    private StringRedisSerializer requireStringKeySerializer(RedisOperations<String, Object> redisOperations) {
        if (redisOperations.getKeySerializer() instanceof StringRedisSerializer keySerializer) {
            return keySerializer;
        }
        throw new IllegalStateException("缓存区域清理要求 RedisTemplate 使用 StringRedisSerializer 作为 Key 序列化器");
    }

    /**
     * 编码缓存名称中的分隔符和转义符，保证缓存名称边界不会与业务 Key 混淆。
     *
     * <p>百分号必须先编码，再编码冒号。例如 {@code rule:index} 会转换为
     * {@code rule%3Aindex}，而字面量 {@code rule%3Aindex} 会转换为
     * {@code rule%253Aindex}，两者不会碰撞。</p>
     *
     * @param cacheName 原始缓存名称
     * @return 可安全作为单个 Redis Key 段的缓存名称
     */
    private static String encodeCacheName(String cacheName) {
        return cacheName.replace("%", "%25").replace(":", "%3A");
    }

    /**
     * 为单个业务 Key 生成稳定的 Redis Cluster Hash Tag。
     *
     * <p>数据 Key 和版本 Key 使用同一个 Tag，可以在 Lua 中原子操作；不同业务 Key 使用
     * 不同摘要，避免整个缓存区域集中到单一 Slot。</p>
     *
     * @param serializedKey 已序列化的业务 Key
     * @return 包含大括号的 Hash Tag 片段
     */
    private static String hashTag(String serializedKey) {
        return "{" + DigestUtil.sha256(serializedKey).substring(0, 24) + "}";
    }

    /**
     * 转义 Redis glob 模式中的特殊字符，确保扫描范围不会因前缀内容被意外扩大。
     *
     * @param value 待转义的固定 Key 前缀
     * @return 可安全用于 MATCH 的字面量模式
     */
    private static String escapeGlob(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' || current == '*' || current == '?' || current == '[' || current == ']') {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }
}
