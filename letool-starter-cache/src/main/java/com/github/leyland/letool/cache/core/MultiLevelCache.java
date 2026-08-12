package com.github.leyland.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.leyland.letool.cache.consistency.CacheConsistencyMode;
import com.github.leyland.letool.cache.consistency.CacheWritePolicy;
import com.github.leyland.letool.cache.exception.CacheException;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 默认的二级缓存实现：L1 使用 Caffeine，本地进程内高速缓存；L2 使用 Redis，跨 JVM 共享缓存。
 *
 * <p>这个类是 letool 缓存 starter 的核心实现，目标是同时兼顾三件事：</p>
 * <ul>
 *     <li>读性能：热点数据优先命中 L1，避免每次访问 Redis。</li>
 *     <li>跨节点一致性：强一致模式下，L1 命中前会校验 Redis 中的缓存区域版本，防止其它 JVM 更新后当前 JVM 继续返回旧值。</li>
 *     <li>容错降级：Redis 异常时自动标记 L2 降级，后续读写跳过 Redis，避免缓存组件把业务请求拖死。</li>
 * </ul>
 *
 * <p>缓存读取流程：</p>
 * <pre>
 * getOrLoad(key)
 *   1. 查询 L1，本地命中且版本新鲜则直接返回。
 *   2. L1 未命中或版本过期时查询 Redis L2。
 *   3. L2 命中后回填 L1，L1 TTL 取 min(配置的 L1 TTL, Redis 剩余 TTL)。
 *   4. L1/L2 都未命中时，同一 JVM 内按 key 加锁，只允许一个线程执行 loader 回源。
 *   5. loader 结果写入 L2 并推进版本，再写入 L1。
 * </pre>
 *
 * <p>关于强一致模式：</p>
 * <p>每个缓存区域维护一个 Redis 版本 key。put/evict 会通过 Lua 脚本把“写业务值/删除业务值”和“推进版本”
 * 放在同一次 Redis 原子操作里。L1 条目记录写入时看到的版本；读取 L1 时再次读取 Redis 当前版本，
 * 只有两者相等才认为本地副本仍然可信。</p>
 */
public class MultiLevelCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCache.class);

    /** Redis 中用于表示“命中空值”的可序列化哨兵，避免 null 穿透数据库。 */
    private static final String REDIS_NULL_SENTINEL = "NULL_SENTINEL";
    /** L2 关闭或非强一致模式下使用的本地固定版本。 */
    private static final long LOCAL_ONLY_VERSION = -1L;
    /** 原子写脚本：先推进区域版本，再设置业务值和 TTL。 */
    private static final String ATOMIC_PUT_SCRIPT = """
            local version = redis.call('INCR', KEYS[2])
            redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
            return version
            """;
    /** 原子删脚本：删除业务 key，并推进区域版本，使其它 JVM 的 L1 立即失效。 */
    private static final String ATOMIC_DELETE_SCRIPT = """
            redis.call('DEL', KEYS[1])
            return redis.call('INCR', KEYS[2])
            """;

    /** DURABLE 模式写回时先检查围栏，禁止未提交事务期间的旧数据库快照进入缓存。 */
    private static final String DURABLE_ATOMIC_PUT_SCRIPT = """
            if redis.call('EXISTS', KEYS[3]) == 1 then return -1 end
            local current = redis.call('GET', KEYS[2])
            if not current then current = '0' end
            if current ~= ARGV[3] then return -1 end
            redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
            return redis.call('INCR', KEYS[2])
            """;

    /** 缓存区域名称，用于注册表、日志、Redis key 拼接和失效广播路由。 */
    private final String name;
    /** L1 本地缓存。value 使用 Object 是为了存储真实值和 NullSentinel。 */
    private final Cache<K, Object> l1Cache;
    /** L1 条目对应的 Redis 区域版本快照，仅在强一致模式下参与判断。 */
    private final Cache<K, Long> l1Versions;
    /** L1 条目写入时看到的区域纪元，用于感知离线期间发生的 evictAll。 */
    private final Cache<K, Long> l1RegionVersions;
    /** Redis 操作工具。为 null 时缓存自动退化为 L1-only。 */
    private final RedisUtil redisUtil;
    /** 业务值序列化器，用于写入 Redis 字符串值。 */
    private final CacheSerializer serializer;
    /** 当前缓存区域的最终配置，已经由 CacheManager 合并全局开关后传入。 */
    private final CacheConfig<K, V> config;
    /** 当前缓存区域的 Redis 键空间，负责键隔离和非阻塞区域清理。 */
    private final RedisCacheKeyspace keyspace;
    /** L2 命中时用于校验 RedisTemplate 反序列化结果的 value 类型；为 null 时跳过严格类型校验。 */
    private final Class<?> valueType;
    /** 当前缓存实例的运行统计。 */
    private final CacheStats stats = new CacheStats();
    /** key 级单飞锁，避免热点 key 在并发未命中时同时回源数据库。 */
    private final ConcurrentMap<K, Object> loadLocks = new ConcurrentHashMap<>();
    /** 跨 JVM L1 失效广播发布器。没有 Redis pub/sub 时会退化为 no-op。 */
    private final CacheInvalidationPublisher invalidationPublisher;
    /** 当前 JVM 缓存节点 ID，用于失效消息去重，避免处理自己发出的消息。 */
    private final String instanceId;
    /** 首次进入 L2 降级时通知 CacheManager，把当前缓存加入恢复探测队列。 */
    private final Runnable degradationListener;

    /** Redis 是否处于降级状态。降级后读写路径会跳过 L2，等待恢复调度探测。 */
    private volatile boolean l2Degraded = false;

    /**
     * 创建 KV 二级缓存实例。
     *
     * <p>该构造器主要用于测试或手动创建缓存；跨 JVM L1 失效广播默认使用 no-op。</p>
     *
     * @param config 缓存区域配置
     * @param redisUtil Redis 操作入口；为 null 时只启用 L1
     * @param serializer 业务值序列化器
     */
    public MultiLevelCache(CacheConfig<K, V> config, RedisUtil redisUtil, CacheSerializer serializer) {
        this(config, redisUtil, serializer, CacheInvalidationPublisher.noop(), "local", () -> { });
    }

    /**
     * 创建完整的 KV 二级缓存实例。
     *
     * <p>该构造器由 {@link CacheManager} 使用，会注入失效广播发布器、当前 JVM 实例 ID 和 L2 降级回调。</p>
     *
     * @param config 缓存区域配置
     * @param redisUtil Redis 操作入口；为 null 时只启用 L1
     * @param serializer 业务值序列化器
     * @param invalidationPublisher 跨 JVM L1 失效广播发布器
     * @param instanceId 当前 JVM 缓存节点 ID
     * @param degradationListener 首次进入 L2 降级时的回调
     */
    @SuppressWarnings("unchecked")
    public MultiLevelCache(CacheConfig<K, V> config,
                           RedisUtil redisUtil,
                           CacheSerializer serializer,
                           CacheInvalidationPublisher invalidationPublisher,
                           String instanceId,
                           Runnable degradationListener) {
        this.name = config.getName();
        this.redisUtil = redisUtil;
        this.serializer = serializer;
        this.config = config;
        this.keyspace = new RedisCacheKeyspace(config.getRedisKeyPrefix(), name);
        this.valueType = config.getValueType();
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.noop() : invalidationPublisher;
        this.instanceId = instanceId == null ? "local" : instanceId;
        this.degradationListener = degradationListener == null ? () -> { } : degradationListener;

        this.l1Cache = (Cache<K, Object>) Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfter(new Expiry<K, Object>() {
                    @Override
                    public long expireAfterCreate(K key, Object value, long currentTime) {
                        // 默认按配置的 L1 TTL 写入；从 Redis 回填时会通过 policy.put 覆盖为更短 TTL。
                        return config.getL1Ttl().toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(K key, Object value, long currentTime, long currentDuration) {
                        // 更新时尽量保留已有剩余 TTL，避免 Redis 即将过期的值在 L1 被“续命”。
                        return currentDuration > 0 ? currentDuration : config.getL1Ttl().toNanos();
                    }

                    @Override
                    public long expireAfterRead(K key, Object value, long currentTime, long currentDuration) {
                        // 读操作不续期，缓存生命周期只由写入/回填时确定。
                        return currentDuration;
                    }
                })
                .recordStats()
                .build();
        this.l1Versions = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .build();
        this.l1RegionVersions = Caffeine.newBuilder()
                .maximumSize(config.getL1MaxSize())
                .expireAfterWrite(config.getL1Ttl())
                .build();
    }

    /**
     * 读穿模式获取缓存值。
     *
     * <p>方法会先查 L1，再查 L2；两级都未命中时调用 loader 回源，并把非 null 结果写回缓存。
     * 如果 loader 返回 null 且配置启用了 null 值缓存，则写入空值哨兵，防止同一个不存在 key 高频穿透。</p>
     *
     * @param key 缓存 key
     * @param loader 数据加载器，通常查询数据库或远程服务
     * @return 缓存值，可能为 null
     */
    public V getOrLoad(K key, Function<K, V> loader) {
        return getOrLoad(key, loader, config.getL2Ttl());
    }

    /**
     * 读穿模式获取缓存值，并为本次 loader 写回指定 L2 TTL。
     *
     * <p>该方法主要服务注解场景，例如 {@code @MultiLevelCacheable(ttl = 60)}。
     * L1 实际 TTL 会取配置 L1 TTL 和传入 TTL 的较小值，避免本地缓存活得比 Redis 更久。</p>
     *
     * @param key 缓存 key
     * @param loader 数据加载器
     * @param ttl 本次写回 L2 的过期时间；为 null、0 或负数时使用配置默认 TTL
     * @return 缓存值，可能为 null
     */
    public V getOrLoad(K key, Function<K, V> loader, Duration ttl) {
        if (key == null) {
            return null;
        }
        if (!isDurableReadSafe(key)) {
            return loadWithoutCaching(key, loader);
        }
        // 第一轮先尝试命中 L1/L2；如果已经命中，不需要进入同步区。
        CacheLookup<V> lookup = getPresentLookup(key);
        if (lookup.hit()) {
            return lookup.value();
        }

        // 同一个 key 只让一个线程回源，其它线程等待后再次检查缓存。
        Object lock = loadLocks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                // 获得锁后必须复查，可能前一个等待线程已经完成 loader 并写入缓存。
                lookup = getPresentLookup(key);
                if (lookup.hit()) {
                    return lookup.value();
                }

                stats.recordMiss();
                stats.recordLoad();
                Long versionBeforeLoad = config.getConsistencyMode() == CacheConsistencyMode.DURABLE
                        ? readConsistencyVersion(key) : null;
                V loaded;
                try {
                    loaded = loader.apply(key);
                } catch (Exception e) {
                    stats.recordLoadFailure();
                    throw CacheException.loaderFailed(e);
                }

                stats.recordLoadSuccess();
                if (loaded != null) {
                    putLoadedValue(key, loaded, ttl, versionBeforeLoad);
                } else if (config.isNullValueCache()) {
                    putLoadedNull(key, versionBeforeLoad);
                }
                return loaded;
            }
        } finally {
            loadLocks.remove(key, lock);
        }
    }

    /**
     * 只读取现有缓存，不触发 loader 回源。
     *
     * @param key 缓存 key
     * @return 当前已缓存的值；L1/L2 都未命中时返回 null
     */
    public V getIfPresent(K key) {
        if (key == null) {
            return null;
        }
        if (!isDurableReadSafe(key)) {
            return null;
        }
        CacheLookup<V> lookup = getPresentLookup(key);
        return lookup.hit() ? lookup.value() : null;
    }

    /**
     * 批量读取现有缓存，不触发 loader 回源。
     *
     * @param keys 缓存 key 集合
     * @return 已命中的 key/value 映射；未命中的 key 不会出现在返回结果中
     */
    public Map<K, V> getAllPresent(Set<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<K, V> result = new HashMap<>();
        for (K key : keys) {
            if (key == null || !isDurableReadSafe(key)) {
                continue;
            }
            CacheLookup<V> lookup = getPresentLookup(key);
            if (lookup.hit()) {
                result.put(key, lookup.value());
            }
        }
        return result;
    }

    /**
     * 使用默认 L2 TTL 写入缓存。
     *
     * @param key 缓存 key
     * @param value 缓存值；为 null 且启用 null 值缓存时写入空值哨兵
     */
    public void put(K key, V value) {
        put(key, value, config.getL2Ttl());
    }

    /**
     * 使用指定 L2 TTL 写入缓存。
     *
     * @param key 缓存 key
     * @param value 缓存值；为 null 且启用 null 值缓存时写入空值哨兵
     * @param ttl L2 过期时间；为 null、0 或负数时使用配置默认 TTL
     */
    public void put(K key, V value, Duration ttl) {
        if (key == null) {
            return;
        }
        if (value == null) {
            if (config.isNullValueCache()) {
                putLoadedNull(key);
            }
            return;
        }
        putLoadedValue(key, value, ttl);
    }

    /**
     * 删除指定 key 的缓存。
     *
     * <p>完整流程会删除当前 JVM 的 L1、删除 Redis L2、推进缓存区域版本，并广播其它 JVM 清理 L1。</p>
     *
     * @param key 缓存 key
     */
    public void evict(K key) {
        if (key == null) {
            return;
        }
        // 当前 JVM 先删 L1，再删 Redis 并推进版本，最后广播其它 JVM 删除自己的 L1。
        evictLocal(key);
        stats.recordEviction();
        deleteFromRedisAndAdvanceVersion(key);
        publishInvalidation(Set.of(key));
    }

    /**
     * 清空当前缓存区域。
     *
     * <p>该方法会清理当前 JVM 的 L1，并尽力删除当前缓存区域前缀下的 Redis key，
     * 最后广播其它 JVM 清理本地副本。</p>
     */
    public void evictAll() {
        evictLocalAll();
        if (isL2Enabled()) {
            try {
                // 使用 SCAN + UNLINK 分批清理当前区域，避免 KEYS 阻塞 Redis 主线程。
                keyspace.scanAndUnlink(redisUtil.getTemplate());
                bumpConsistencyVersion();
            } catch (Exception e) {
                markL2Degraded(e);
            }
        }
        invalidationPublisher.publish(CacheInvalidationMessage.all(name, instanceId));
    }

    /**
     * 仅删除当前 JVM 的 L1 条目。
     *
     * <p>该方法供跨节点失效监听器调用，不删除 Redis，也不会再次广播，避免形成广播循环。</p>
     *
     * @param key 缓存 key
     */
    public void evictLocal(K key) {
        if (config.isL1Enabled() && key != null) {
            l1Cache.invalidate(key);
            l1Versions.invalidate(key);
            l1RegionVersions.invalidate(key);
        }
    }

    /**
     * 按广播中的字符串表示匹配并清理真实 L1 key。
     *
     * <p>发布端和接收端共用缓存区域配置的 key 序列化器，避免复合 key 的字符串表示不稳定。</p>
     *
     * @param serializedKey 广播中的业务 key 字符串
     */
    void evictLocalSerializedKey(String serializedKey) {
        if (!config.isL1Enabled() || serializedKey == null) {
            return;
        }
        for (K candidate : l1Cache.asMap().keySet()) {
            if (serializedKey.equals(serializeKey(candidate))) {
                evictLocal(candidate);
            }
        }
    }

    /**
     * 仅清空当前 JVM 的 L1 缓存区域。
     *
     * <p>该方法供跨节点失效监听器调用，不删除 Redis，也不会再次广播。</p>
     */
    public void evictLocalAll() {
        if (config.isL1Enabled()) {
            l1Cache.invalidateAll();
            l1Versions.invalidateAll();
            l1RegionVersions.invalidateAll();
        }
    }

    /**
     * 获取缓存统计快照。
     *
     * @return 当前缓存实例的统计对象
     */
    public CacheStats stats() {
        return stats;
    }

    /**
     * 获取当前 JVM L1 估算条目数。
     *
     * @return L1 估算条目数；未启用 L1 时返回 0
     */
    public long estimatedSize() {
        return config.isL1Enabled() ? l1Cache.estimatedSize() : 0;
    }

    /**
     * 当前缓存是否已经因为 Redis 异常进入 L2 降级。
     *
     * @return true 表示 L2 已降级，读写会暂时跳过 Redis
     */
    public boolean isL2Degraded() {
        return l2Degraded;
    }

    /**
     * 尝试恢复 L2 访问。
     *
     * <p>该方法只做轻量 Redis 探测，不预热数据。恢复成功后后续读写会重新访问 Redis。</p>
     *
     * @return true 表示当前 L2 可用或已恢复
     */
    public boolean tryRecoverL2() {
        if (!l2Degraded) {
            return true;
        }
        try {
            redisUtil.hasKey(keyspace.healthCheckKey());
            l2Degraded = false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取缓存区域名称。
     *
     * @return 缓存区域名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取数据库修改与缓存失效之间的一致性模式。
     *
     * @return 当前缓存区域的一致性模式
     */
    public CacheConsistencyMode getConsistencyMode() {
        return config.getConsistencyMode();
    }

    /**
     * 获取数据库修改成功后的缓存处理策略。
     *
     * @return 当前缓存区域的写策略
     */
    public CacheWritePolicy getWritePolicy() {
        return config.getWritePolicy();
    }

    private CacheLookup<V> getPresentLookup(K key) {
        if (config.isL1Enabled()) {
            // 强一致模式下，getFreshLocal 不只是读 Caffeine，还会校验 Redis 版本。
            CacheLookup<V> localLookup = getFreshLocal(key);
            if (localLookup.hit()) {
                stats.recordL1Hit();
                return localLookup;
            }
        }

        if (isL2Enabled()) {
            CacheLookup<V> l2Lookup = getFromL2(key);
            if (l2Lookup.hit()) {
                stats.recordL2Hit();
                if (config.isL1Enabled()) {
                    // Redis 命中后回填 L1，但本地 TTL 不能超过 Redis 剩余 TTL。
                    putToL1(key, toLocalValue(l2Lookup), l1TtlForL2Hit(key), l2Lookup.version());
                }
                return l2Lookup;
            }
        }
        return CacheLookup.miss();
    }

    private CacheLookup<V> getFreshLocal(K key) {
        Object localValue = l1Cache.getIfPresent(key);
        if (localValue == null) {
            return CacheLookup.miss();
        }
        if (!isLocalVersionFresh(key)) {
            evictLocal(key);
            return CacheLookup.miss();
        }
        if (localValue instanceof NullSentinel) {
            return CacheLookup.nullHit(localVersion(key));
        }
        @SuppressWarnings("unchecked")
        V value = (V) localValue;
        return CacheLookup.hit(value, localVersion(key));
    }

    private boolean isLocalVersionFresh(K key) {
        if (!config.isL1Enabled() || !config.isStrongConsistency() || !isL2Configured()) {
            return true;
        }
        if (l2Degraded) {
            return false;
        }
        // 本地没有版本快照时，不能证明该值新鲜，宁愿丢弃后走 L2。
        Long localVersion = l1Versions.getIfPresent(key);
        if (localVersion == null) {
            return false;
        }
        Long remoteVersion = readConsistencyVersion(key);
        Long localRegionVersion = l1RegionVersions.getIfPresent(key);
        Long remoteRegionVersion = readRegionVersion();
        return remoteVersion != null && remoteVersion.equals(localVersion)
                && remoteRegionVersion != null && remoteRegionVersion.equals(localRegionVersion);
    }

    private Long localVersion(K key) {
        if (!config.isL1Enabled() || !config.isStrongConsistency() || !isL2Configured()) {
            return LOCAL_ONLY_VERSION;
        }
        return l1Versions.getIfPresent(key);
    }

    private void putLoadedValue(K key, V value, Duration ttl) {
        putLoadedValue(key, value, ttl, null);
    }

    private void putLoadedValue(K key, V value, Duration ttl, Long expectedVersion) {
        Duration l2Ttl = effectiveTtl(ttl, config.getL2Ttl());
        Long version = writeToRedisAndAdvanceVersion(key, value, l2Ttl, expectedVersion);
        putToL1(key, value, min(config.getL1Ttl(), l2Ttl), version);
    }

    private void putLoadedNull(K key) {
        putLoadedNull(key, null);
    }

    private void putLoadedNull(K key, Long expectedVersion) {
        Long version = writeToRedisAndAdvanceVersion(
                key, REDIS_NULL_SENTINEL, config.getNullValueTtl(), expectedVersion);
        putToL1(key, NullSentinel.INSTANCE, min(config.getL1Ttl(), config.getNullValueTtl()), version);
    }

    private void putToL1(K key, Object value, Duration ttl, Long version) {
        if (!config.isL1Enabled()) {
            return;
        }
        if (key == null || value == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            evictLocal(key);
            return;
        }
        if (config.isStrongConsistency() && isL2Configured() && (l2Degraded || version == null)) {
            // 强一致模式下没有 Redis 版本就不写 L1，避免把无法证明新鲜的数据留在本地。
            return;
        }
        l1Cache.policy().expireVariably()
                .ifPresentOrElse(policy -> policy.put(key, value, ttl), () -> l1Cache.put(key, value));
        if (config.isStrongConsistency() && isL2Enabled()) {
            l1Versions.put(key, version);
            Long regionVersion = readRegionVersion();
            if (regionVersion == null) {
                evictLocal(key);
            } else {
                l1RegionVersions.put(key, regionVersion);
            }
        }
    }

    private Object toLocalValue(CacheLookup<V> lookup) {
        return lookup.nullValue() ? NullSentinel.INSTANCE : lookup.value();
    }

    private CacheLookup<V> getFromL2(K key) {
        // 为了避免“读 Redis 值期间并发写入/删除”的窗口，强一致模式下读值前后各读一次版本。
        Long versionBefore = config.isStrongConsistency() ? readConsistencyVersion(key) : LOCAL_ONLY_VERSION;
        Long regionBefore = config.isStrongConsistency() ? readRegionVersion() : LOCAL_ONLY_VERSION;
        if (config.isStrongConsistency() && (versionBefore == null || regionBefore == null)) {
            return CacheLookup.miss();
        }
        try {
            Object cachedValue = redisUtil.boundValueOps(redisKey(key)).get();
            if (cachedValue == null) {
                return CacheLookup.miss();
            }
            Long versionAfter = config.isStrongConsistency() ? readConsistencyVersion(key) : LOCAL_ONLY_VERSION;
            Long regionAfter = config.isStrongConsistency() ? readRegionVersion() : LOCAL_ONLY_VERSION;
            if (config.isStrongConsistency()
                    && (!versionBefore.equals(versionAfter) || !regionBefore.equals(regionAfter))) {
                return CacheLookup.miss();
            }
            long stableVersion = versionAfter == null ? LOCAL_ONLY_VERSION : versionAfter;
            if (REDIS_NULL_SENTINEL.equals(cachedValue)) {
                return CacheLookup.nullHit(stableVersion);
            }
            if (isExpectedValueType(cachedValue)) {
                if (isCollectionOfRawJson(cachedValue)) {
                    log.warn(
                            "L2 cache [{}] collection element type was not deserialized safely, fallback to loader",
                            name
                    );
                    return CacheLookup.miss();
                }
                @SuppressWarnings("unchecked")
                V value = (V) cachedValue;
                return CacheLookup.hit(value, stableVersion);
            }
            log.warn(
                    "L2 cache [{}] type mismatch, ignore cached value: expected={}, actual={}",
                    name,
                    valueType.getName(),
                    cachedValue.getClass().getName()
            );
            return CacheLookup.miss();
        } catch (Exception e) {
            markL2Degraded(e);
            return CacheLookup.miss();
        }
    }

    private Duration l1TtlForL2Hit(K key) {
        try {
            long ttlMillis = redisUtil.getExpire(redisKey(key), TimeUnit.MILLISECONDS);
            if (ttlMillis < 0) {
                return config.getL1Ttl();
            }
            if (ttlMillis == 0) {
                return Duration.ZERO;
            }
            return min(config.getL1Ttl(), Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            markL2Degraded(e);
            return Duration.ZERO;
        }
    }

    private Long writeToRedisAndAdvanceVersion(
            K key, Object value, Duration ttl, Long expectedVersion) {
        if (!isL2Enabled()) {
            return LOCAL_ONLY_VERSION;
        }
        try {
            if (config.isStrongConsistency()) {
                // 使用 Lua 保证业务值写入和版本推进在 Redis 单线程内原子完成。
                // 预序列化 value：保留 @type 元数据，确保后续 get 时能正确反序列化。
                // 使用 executeScriptRaw：TTL 作为纯数字字符串传递，避免被 Fastjson2
                // 的 WriteClassName 包装成 {"@type":"java.lang.Long","value":259200000}，
                // 导致 Redis PSETEX 无法解析 TTL。
                byte[] rawValue = redisUtil.serializeValue(value);
                String script = config.getConsistencyMode() == CacheConsistencyMode.DURABLE
                        ? DURABLE_ATOMIC_PUT_SCRIPT
                        : ATOMIC_PUT_SCRIPT;
                List<String> keys = config.getConsistencyMode() == CacheConsistencyMode.DURABLE
                        ? List.of(redisKey(key), versionKey(key), fenceKey(key))
                        : List.of(redisKey(key), versionKey(key));
                Object[] arguments = config.getConsistencyMode() == CacheConsistencyMode.DURABLE
                        ? new Object[]{rawValue, String.valueOf(ttl.toMillis()),
                        String.valueOf(expectedVersion == null ? readConsistencyVersion(key) : expectedVersion)}
                        : new Object[]{rawValue, String.valueOf(ttl.toMillis())};
                Long version = toLong(redisUtil.executeScriptRaw(script, keys, arguments));
                return version != null && version >= 0 ? version : null;
            }
            redisUtil.boundValueOps(redisKey(key)).set(value, ttl);
            return LOCAL_ONLY_VERSION;
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private Long deleteFromRedisAndAdvanceVersion(K key) {
        if (!isL2Enabled()) {
            return LOCAL_ONLY_VERSION;
        }
        try {
            if (config.isStrongConsistency()) {
                // 删除也要推进版本，否则其它 JVM 可能继续命中旧 L1。
                return toLong(redisUtil.executeScript(
                        ATOMIC_DELETE_SCRIPT, List.of(redisKey(key), versionKey(key))));
            }
            redisUtil.delete(redisKey(key));
            return LOCAL_ONLY_VERSION;
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private Long readConsistencyVersion(K key) {
        if (!config.isStrongConsistency() || !isL2Enabled()) {
            return LOCAL_ONLY_VERSION;
        }
        try {
            Object raw = redisUtil.boundValueOps(versionKey(key)).get();
            if (raw == null || raw instanceof String stringValue && stringValue.isBlank()) {
                return 0L;
            }
            return toLong(raw);
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private Long bumpConsistencyVersion() {
        if (!config.isStrongConsistency() || !isL2Enabled()) {
            return LOCAL_ONLY_VERSION;
        }
        try {
            return redisUtil.increment(keyspace.regionVersionKey(), 1);
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private Long readRegionVersion() {
        if (!config.isStrongConsistency() || !isL2Enabled()) {
            return LOCAL_ONLY_VERSION;
        }
        try {
            Object raw = redisUtil.boundValueOps(keyspace.regionVersionKey()).get();
            if (raw == null || raw instanceof String stringValue && stringValue.isBlank()) {
                return 0L;
            }
            return toLong(raw);
        } catch (Exception exception) {
            markL2Degraded(exception);
            return null;
        }
    }

    private void publishInvalidation(Set<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> serializedKeys = keys.stream()
                .filter(key -> key != null)
                .map(this::serializeKey)
                .toList();
        invalidationPublisher.publish(CacheInvalidationMessage.keys(name, serializedKeys, instanceId));
    }

    private void markL2Degraded(Exception cause) {
        stats.recordL2Degraded();
        if (!l2Degraded) {
            l2Degraded = true;
            // 只在第一次降级时登记恢复任务，避免 Redis 抖动时重复加入队列。
            degradationListener.run();
            log.warn(
                    "L2 cache [{}] degraded due to Redis error, causeType={}",
                    name,
                    cause.getClass().getSimpleName()
            );
            log.debug("L2 cache degradation detail", cause);
        }
    }

    private boolean isL2Enabled() {
        return isL2Configured() && !l2Degraded;
    }

    /**
     * 判断当前缓存是否配置了 Redis L2，不受临时降级状态影响。
     *
     * @return 配置了 Redis L2 时返回 {@code true}
     */
    private boolean isL2Configured() {
        return redisUtil != null && config.isL2Enabled();
    }

    private boolean isExpectedValueType(Object value) {
        return valueType == null || valueType.isInstance(value);
    }

    private boolean isCollectionOfRawJson(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return false;
        }
        return collection.stream().anyMatch(Map.class::isInstance);
    }

    private String redisKey(K key) {
        String serializedKey = serializeKey(key);
        return config.isStrongConsistency()
                ? keyspace.clusteredKey(serializedKey)
                : keyspace.key(serializedKey);
    }

    private String versionKey(K key) {
        return keyspace.versionKey(serializeKey(key));
    }

    private String fenceKey(K key) {
        return keyspace.fenceKey(serializeKey(key));
    }

    /**
     * 返回 Redis Key、版本、围栏、Outbox 和失效广播共同使用的业务 key 字符串。
     *
     * @param key 业务 key
     * @return 稳定序列化结果
     */
    public String serializeKey(K key) {
        return config.serializeKey(key);
    }

    /**
     * DURABLE 读取必须能确认 Redis 中不存在写围栏；Redis 不可达时按失败关闭处理。
     */
    private boolean isDurableReadSafe(K key) {
        if (config.getConsistencyMode() != CacheConsistencyMode.DURABLE) {
            return true;
        }
        if (!isL2Enabled()) {
            return false;
        }
        try {
            return !redisUtil.hasKey(fenceKey(key));
        } catch (Exception exception) {
            markL2Degraded(exception);
            return false;
        }
    }

    /**
     * 围栏存在或 Redis 状态未知时直接回源，不把结果写入任何一级缓存。
     */
    private V loadWithoutCaching(K key, Function<K, V> loader) {
        stats.recordMiss();
        stats.recordLoad();
        try {
            V loaded = loader.apply(key);
            stats.recordLoadSuccess();
            return loaded;
        } catch (Exception exception) {
            stats.recordLoadFailure();
            throw CacheException.loaderFailed(exception);
        }
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static Duration effectiveTtl(Duration ttl, Duration fallback) {
        return ttl == null || ttl.isZero() || ttl.isNegative() ? fallback : ttl;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private record CacheLookup<T>(boolean hit, T value, boolean nullValue, Long version) {
        static <T> CacheLookup<T> miss() {
            return new CacheLookup<>(false, null, false, null);
        }

        static <T> CacheLookup<T> hit(T value, Long version) {
            return new CacheLookup<>(true, value, false, version);
        }

        static <T> CacheLookup<T> nullHit(Long version) {
            return new CacheLookup<>(true, null, true, version);
        }
    }
}
