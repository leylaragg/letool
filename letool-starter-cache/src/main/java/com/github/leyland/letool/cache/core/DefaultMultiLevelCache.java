package com.github.leyland.letool.cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
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
public class DefaultMultiLevelCache<K, V> implements MultiLevelCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(DefaultMultiLevelCache.class);

    /** Redis 中用于表示“命中空值”的可序列化哨兵，避免 null 穿透数据库。 */
    private static final String REDIS_NULL_SENTINEL = "NULL_SENTINEL";
    /** 每个缓存区域对应一个版本 key，业务 key 和版本 key 共用同一个 Redis 前缀。 */
    private static final String VERSION_KEY_SUFFIX = "__version__";
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

    /** 缓存区域名称，用于注册表、日志、Redis key 拼接和失效广播路由。 */
    private final String name;
    /** L1 本地缓存。value 使用 Object 是为了存储真实值和 NullSentinel。 */
    private final Cache<K, Object> l1Cache;
    /** L1 条目对应的 Redis 区域版本快照，仅在强一致模式下参与判断。 */
    private final Cache<K, Long> l1Versions;
    /** Redis 操作工具。为 null 时缓存自动退化为 L1-only。 */
    private final RedisUtil redisUtil;
    /** 业务值序列化器，用于写入 Redis 字符串值。 */
    private final CacheSerializer serializer;
    /** 当前缓存区域的最终配置，已经由 CacheManager 合并全局开关后传入。 */
    private final CacheConfig<K, V> config;
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

    public DefaultMultiLevelCache(CacheConfig<K, V> config, RedisUtil redisUtil, CacheSerializer serializer) {
        this(config, redisUtil, serializer, CacheInvalidationPublisher.noop(), "local", () -> { });
    }

    @SuppressWarnings("unchecked")
    public DefaultMultiLevelCache(CacheConfig<K, V> config,
                                  RedisUtil redisUtil,
                                  CacheSerializer serializer,
                                  CacheInvalidationPublisher invalidationPublisher,
                                  String instanceId,
                                  Runnable degradationListener) {
        this.name = config.getName();
        this.redisUtil = redisUtil;
        this.serializer = serializer;
        this.config = config;
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
    }

    @Override
    public V getOrLoad(K key, Function<K, V> loader) {
        return getOrLoad(key, loader, config.getL2Ttl());
    }

    @Override
    public V getOrLoad(K key, Function<K, V> loader, Duration ttl) {
        if (key == null) {
            return null;
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
                V loaded;
                try {
                    loaded = loader.apply(key);
                } catch (Exception e) {
                    stats.recordLoadFailure();
                    throw new CacheException("Failed to load cache value for key: " + key + " in cache: " + name, e);
                }

                stats.recordLoadSuccess();
                if (loaded != null) {
                    putLoadedValue(key, loaded, ttl);
                } else if (config.isNullValueCache()) {
                    putLoadedNull(key);
                }
                return loaded;
            }
        } finally {
            loadLocks.remove(key, lock);
        }
    }

    @Override
    public V getIfPresent(K key) {
        if (key == null) {
            return null;
        }
        CacheLookup<V> lookup = getPresentLookup(key);
        return lookup.hit() ? lookup.value() : null;
    }

    @Override
    public Map<K, V> getAllPresent(Set<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<K, V> result = new HashMap<>();
        for (K key : keys) {
            CacheLookup<V> lookup = getPresentLookup(key);
            if (lookup.hit()) {
                result.put(key, lookup.value());
            }
        }
        return result;
    }

    @Override
    public void put(K key, V value) {
        put(key, value, config.getL2Ttl());
    }

    @Override
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

    @Override
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

    @Override
    public void evictAll() {
        evictLocalAll();
        if (isL2Enabled()) {
            try {
                // 只删除当前缓存区域下的 key，避免误删其它缓存区域或业务数据。
                Set<String> keys = redisUtil.getTemplate().keys(config.getRedisKeyPrefix() + name + ":*");
                if (keys != null && !keys.isEmpty()) {
                    redisUtil.delete(keys);
                }
                bumpConsistencyVersion();
            } catch (Exception e) {
                markL2Degraded(e);
            }
        }
        invalidationPublisher.publish(CacheInvalidationMessage.all(name, instanceId));
    }

    @Override
    public void evictLocal(K key) {
        if (config.isL1Enabled() && key != null) {
            l1Cache.invalidate(key);
            l1Versions.invalidate(key);
        }
    }

    @Override
    public void evictLocalAll() {
        if (config.isL1Enabled()) {
            l1Cache.invalidateAll();
            l1Versions.invalidateAll();
        }
    }

    @Override
    public CacheStats stats() {
        return stats;
    }

    @Override
    public long estimatedSize() {
        return config.isL1Enabled() ? l1Cache.estimatedSize() : 0;
    }

    @Override
    public boolean isL2Degraded() {
        return l2Degraded;
    }

    @Override
    public boolean tryRecoverL2() {
        if (!l2Degraded) {
            return true;
        }
        try {
            redisUtil.hasKey(versionKey());
            l2Degraded = false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return name;
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
        if (!config.isL1Enabled() || !config.isStrongConsistency() || !isL2Enabled()) {
            return true;
        }
        // 本地没有版本快照时，不能证明该值新鲜，宁愿丢弃后走 L2。
        Long localVersion = l1Versions.getIfPresent(key);
        if (localVersion == null) {
            return false;
        }
        Long remoteVersion = readConsistencyVersion();
        return remoteVersion != null && remoteVersion.equals(localVersion);
    }

    private Long localVersion(K key) {
        if (!config.isL1Enabled() || !config.isStrongConsistency() || !isL2Enabled()) {
            return LOCAL_ONLY_VERSION;
        }
        return l1Versions.getIfPresent(key);
    }

    private void putLoadedValue(K key, V value, Duration ttl) {
        Duration l2Ttl = effectiveTtl(ttl, config.getL2Ttl());
        Long version = writeToRedisAndAdvanceVersion(key, value, l2Ttl);
        putToL1(key, value, min(config.getL1Ttl(), l2Ttl), version);
    }

    private void putLoadedNull(K key) {
        Long version = writeToRedisAndAdvanceVersion(key, REDIS_NULL_SENTINEL, config.getNullValueTtl());
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
        if (config.isStrongConsistency() && isL2Enabled() && version == null) {
            // 强一致模式下没有 Redis 版本就不写 L1，避免把无法证明新鲜的数据留在本地。
            return;
        }
        l1Cache.policy().expireVariably()
                .ifPresentOrElse(policy -> policy.put(key, value, ttl), () -> l1Cache.put(key, value));
        if (config.isStrongConsistency() && isL2Enabled()) {
            l1Versions.put(key, version);
        }
    }

    private Object toLocalValue(CacheLookup<V> lookup) {
        return lookup.nullValue() ? NullSentinel.INSTANCE : lookup.value();
    }

    private CacheLookup<V> getFromL2(K key) {
        // 为了避免“读 Redis 值期间并发写入/删除”的窗口，强一致模式下读值前后各读一次版本。
        Long versionBefore = config.isStrongConsistency() ? readConsistencyVersion() : LOCAL_ONLY_VERSION;
        if (config.isStrongConsistency() && versionBefore == null) {
            return CacheLookup.miss();
        }
        try {
            Object cachedValue = redisUtil.boundValueOps(redisKey(key)).get();
            if (cachedValue == null) {
                return CacheLookup.miss();
            }
            Long versionAfter = config.isStrongConsistency() ? readConsistencyVersion() : LOCAL_ONLY_VERSION;
            if (config.isStrongConsistency() && !versionBefore.equals(versionAfter)) {
                return CacheLookup.miss();
            }
            long stableVersion = versionAfter == null ? LOCAL_ONLY_VERSION : versionAfter;
            if (REDIS_NULL_SENTINEL.equals(cachedValue)) {
                return CacheLookup.nullHit(stableVersion);
            }
            if (isExpectedValueType(cachedValue)) {
                if (isCollectionOfRawJson(cachedValue)) {
                    log.warn("L2 cache [{}] collection element type was not deserialized safely, fallback to loader: key={}",
                            name, redisKey(key));
                    return CacheLookup.miss();
                }
                @SuppressWarnings("unchecked")
                V value = (V) cachedValue;
                return CacheLookup.hit(value, stableVersion);
            }
            log.warn("L2 cache [{}] type mismatch, ignore cached value: key={}, expected={}, actual={}",
                    name, redisKey(key), valueType.getName(), cachedValue.getClass().getName());
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

    private Long writeToRedisAndAdvanceVersion(K key, Object value, Duration ttl) {
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
                return toLong(redisUtil.executeScriptRaw(
                        ATOMIC_PUT_SCRIPT,
                        List.of(redisKey(key), versionKey()),
                        rawValue,
                        String.valueOf(ttl.toMillis())));
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
                return toLong(redisUtil.executeScript(ATOMIC_DELETE_SCRIPT, List.of(redisKey(key), versionKey())));
            }
            redisUtil.delete(redisKey(key));
            return LOCAL_ONLY_VERSION;
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private Long readConsistencyVersion() {
        if (!config.isStrongConsistency() || !isL2Enabled()) {
            return LOCAL_ONLY_VERSION;
        }
        try {
            Object raw = redisUtil.boundValueOps(versionKey()).get();
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
            return redisUtil.increment(versionKey(), 1);
        } catch (Exception e) {
            markL2Degraded(e);
            return null;
        }
    }

    private void publishInvalidation(Set<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> serializedKeys = keys.stream()
                .filter(key -> key != null)
                .map(String::valueOf)
                .toList();
        invalidationPublisher.publish(CacheInvalidationMessage.keys(name, serializedKeys, instanceId));
    }

    private void markL2Degraded(Exception cause) {
        stats.recordL2Degraded();
        if (!l2Degraded) {
            l2Degraded = true;
            // 只在第一次降级时登记恢复任务，避免 Redis 抖动时重复加入队列。
            degradationListener.run();
            log.warn("L2 cache [{}] degraded due to Redis error: {}", name, cause.getMessage());
        }
    }

    private boolean isL2Enabled() {
        return redisUtil != null && config.isL2Enabled() && !l2Degraded;
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
        return config.getRedisKeyPrefix() + name + ":" + key;
    }

    private String versionKey() {
        return config.getRedisKeyPrefix() + name + ":" + VERSION_KEY_SUFFIX;
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
