package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.cache.exception.CacheException;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.tool.redis.RedisUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * letool 缓存管理器，是所有缓存实例的注册中心和工厂。
 *
 * <p>核心职责：</p>
 * <ul>
 *     <li>按缓存名称创建并复用 {@link MultiLevelCache} KV 缓存实例。</li>
 *     <li>按缓存名称创建并复用 {@link MultiLevelSetCache} Set 缓存实例。</li>
 *     <li>合并全局 L1/L2 开关和单个缓存区域配置，生成最终生效配置。</li>
 *     <li>保存当前 JVM 的 instanceId，用于跨节点失效广播去重。</li>
 *     <li>记录已经进入 L2 降级的缓存，供恢复调度器定时探测。</li>
 * </ul>
 *
 * <p>线程安全：</p>
 * <p>内部使用 {@link ConcurrentHashMap#computeIfAbsent(Object, java.util.function.Function)} 保证同名缓存
 * 在并发场景下只会创建一次。调用方可以把本类作为 Spring 单例 Bean 注入使用。</p>
 */
public class CacheManager {

    /** KV 缓存注册表：缓存名称 -> 缓存实例。 */
    private final Map<String, MultiLevelCache<?, ?>> caches = new ConcurrentHashMap<>();
    /** Set 缓存注册表：缓存名称 -> 缓存实例。 */
    private final Map<String, MultiLevelSetCache<?, ?>> setCaches = new ConcurrentHashMap<>();
    /** Redis 操作入口。为 null 时所有缓存都会退化为本地缓存。 */
    private final RedisUtil redisUtil;
    /** KV 缓存使用的序列化器。 */
    private final CacheSerializer serializer;
    /** 全局 L1 开关；最终会和单缓存配置做 AND 合并。 */
    private final boolean l1Enabled;
    /** 全局 L2 开关；没有 RedisUtil 时强制为 false。 */
    private final boolean l2Enabled;
    /** 全局 Redis key 前缀。 */
    private final String globalKeyPrefix;
    /** L1 失效广播发布器。未启用广播时为 no-op。 */
    private final CacheInvalidationPublisher invalidationPublisher;
    /** 当前 JVM 缓存节点 ID，用于忽略自己发出的失效消息。 */
    private final String instanceId = UUID.randomUUID().toString();
    /** 已经进入 L2 降级状态的 KV 缓存名称。 */
    private final Set<String> degradedCaches = ConcurrentHashMap.newKeySet();
    /** 已经进入 L2 降级状态的 Set 缓存名称。 */
    private final Set<String> degradedSetCaches = ConcurrentHashMap.newKeySet();

    /**
     * 兼容旧版本的构造器。只要传入 RedisUtil，就默认启用 L2；否则为 L1-only。
     */
    public CacheManager(RedisUtil redisUtil, CacheSerializer serializer) {
        this(redisUtil, serializer, true, redisUtil != null, "letool:cache:", CacheInvalidationPublisher.noop());
    }

    /**
     * 完整构造器，由自动配置使用。
     *
     * @param redisUtil Redis 操作入口，为 null 时不启用 L2
     * @param serializer KV 缓存值序列化器
     * @param l1Enabled 全局 L1 开关
     * @param l2Enabled 全局 L2 开关，最终还会受 redisUtil 是否存在影响
     * @param globalKeyPrefix 全局 Redis key 前缀
     * @param invalidationPublisher 跨 JVM L1 失效广播发布器
     */
    public CacheManager(RedisUtil redisUtil,
                        CacheSerializer serializer,
                        boolean l1Enabled,
                        boolean l2Enabled,
                        String globalKeyPrefix,
                        CacheInvalidationPublisher invalidationPublisher) {
        this.redisUtil = redisUtil;
        this.serializer = serializer;
        this.l1Enabled = l1Enabled;
        this.l2Enabled = redisUtil != null && l2Enabled;
        this.globalKeyPrefix = globalKeyPrefix == null || globalKeyPrefix.isBlank() ? "letool:cache:" : globalKeyPrefix;
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.noop() : invalidationPublisher;
    }

    /**
     * 获取或创建 KV 二级缓存实例。
     *
     * <p>同名缓存只会创建一次。如果后续使用同名但不同配置再次调用，本方法会返回首次创建的实例；
     * 因此缓存名称应当能唯一表达业务含义，例如 {@code user:byId}、{@code rule:runtime:byCode}。</p>
     *
     * @param config 缓存区域配置
     * @param <K> 缓存 key 类型
     * @param <V> 缓存 value 类型
     * @return 已存在或新创建的缓存实例
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelCache<K, V> getOrCreate(CacheConfig<K, V> config) {
        return (MultiLevelCache<K, V>) caches.computeIfAbsent(config.getName(),
                name -> createCache(config));
    }

    /**
     * 创建 KV 缓存实例。创建前会先合并全局配置，避免调用方手动处理 L1/L2 总开关。
     */
    private <K, V> MultiLevelCache<K, V> createCache(CacheConfig<K, V> config) {
        CacheConfig<K, V> effectiveConfig = effectiveConfig(config);
        return new DefaultMultiLevelCache<>(
                effectiveConfig,
                redisUtil,
                serializer,
                invalidationPublisher,
                instanceId,
                () -> degradedCaches.add(effectiveConfig.getName()));
    }

    /**
     * 获取或创建 String key 的 Set 缓存，成员类型使用默认 Long。
     */
    @SuppressWarnings("unchecked")
    public <V> MultiLevelSetCache<String, V> getOrCreateSetCache(CacheConfig<String, V> config) {
        return (MultiLevelSetCache<String, V>) getOrCreateSetCache(config, Function.identity(), null);
    }

    /**
     * 获取或创建自定义 key 类型的 Set 缓存。
     *
     * @param keySerializer 负责把业务 key 转成 Redis key 后缀，也用于失效广播中的 key 表示
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelSetCache<K, V> getOrCreateSetCache(CacheConfig<K, V> config,
                                                                Function<K, String> keySerializer) {
        return (MultiLevelSetCache<K, V>) getOrCreateSetCache(config, keySerializer, null);
    }

    /**
     * 获取或创建 Set 缓存，并显式指定成员类型。
     *
     * <p>如果成员是 String、Integer 等非 Long 类型，建议使用该重载，避免 Redis 读取后按默认 Long 转换。</p>
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelSetCache<K, V> getOrCreateSetCache(CacheConfig<K, V> config,
                                                                Function<K, String> keySerializer,
                                                                Class<V> memberType) {
        return (MultiLevelSetCache<K, V>) setCaches.computeIfAbsent(config.getName(),
                name -> createSetCache(config, keySerializer, memberType));
    }

    /**
     * 创建 Set 缓存实例，并把首次降级回调接入管理器的待恢复集合。
     */
    private <K, V> MultiLevelSetCache<K, V> createSetCache(CacheConfig<K, V> config,
                                                            Function<K, String> keySerializer,
                                                            Class<V> memberType) {
        CacheConfig<K, V> effectiveConfig = effectiveConfig(config);
        return new MultiLevelSetCache<>(
                effectiveConfig,
                redisUtil,
                keySerializer,
                memberType,
                invalidationPublisher,
                instanceId,
                () -> degradedSetCaches.add(effectiveConfig.getName()));
    }

    /**
     * 合并全局配置和单缓存配置。
     *
     * <p>全局开关优先级更高：如果全局关闭 L1/L2，单个缓存不能重新打开。
     * 其它参数仍由单缓存配置决定，方便不同业务缓存设置不同容量和 TTL。</p>
     */
    private <K, V> CacheConfig<K, V> effectiveConfig(CacheConfig<K, V> config) {
        String prefix = config.getRedisKeyPrefix() == null || config.getRedisKeyPrefix().isBlank()
                ? globalKeyPrefix
                : config.getRedisKeyPrefix();
        return CacheConfig.<K, V>builder(config.getName())
                .l1Enabled(l1Enabled && config.isL1Enabled())
                .l1MaxSize(config.getL1MaxSize())
                .l1Ttl(config.getL1Ttl())
                .l2Ttl(config.getL2Ttl())
                .l2Enabled(l2Enabled && config.isL2Enabled())
                .strongConsistency(config.isStrongConsistency())
                .nullValueCache(config.isNullValueCache())
                .nullValueTtl(config.getNullValueTtl())
                .redisKeyPrefix(prefix)
                .build();
    }

    /**
     * 获取已经注册过的 KV 缓存。
     *
     * @throws CacheException 如果缓存尚未通过 {@link #getOrCreate(CacheConfig)} 创建
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelCache<K, V> get(String name) {
        MultiLevelCache<?, ?> cache = caches.get(name);
        if (cache == null) {
            throw new CacheException("Cache not found: " + name + ". Use getOrCreate() first.");
        }
        return (MultiLevelCache<K, V>) cache;
    }

    /**
     * 从管理器中移除缓存实例，并清理降级记录。
     */
    public void remove(String name) {
        caches.remove(name);
        setCaches.remove(name);
        degradedCaches.remove(name);
        degradedSetCaches.remove(name);
    }

    /**
     * 返回所有已经注册的 KV 缓存实例。
     */
    public Collection<MultiLevelCache<?, ?>> getAll() {
        return caches.values();
    }

    /**
     * 当前 JVM 缓存节点 ID。
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * 仅清理当前 JVM 的某个 L1 条目。
     *
     * <p>该方法供失效监听器调用，不删除 Redis，也不会再次广播，避免形成广播循环。</p>
     */
    public void evictLocal(String cacheName, String key) {
        MultiLevelCache<String, ?> cache = getCache(cacheName);
        if (cache != null) {
            cache.evictLocal(key);
            return;
        }
        MultiLevelSetCache<String, ?> setCache = getSetCache(cacheName);
        if (setCache != null) {
            setCache.evictLocal(key);
        }
    }

    /**
     * 仅清空当前 JVM 的某个缓存区域 L1。
     */
    public void evictLocalAll(String cacheName) {
        MultiLevelCache<?, ?> cache = caches.get(cacheName);
        if (cache != null) {
            cache.evictLocalAll();
            return;
        }
        MultiLevelSetCache<?, ?> setCache = setCaches.get(cacheName);
        if (setCache != null) {
            setCache.evictLocalAll();
        }
    }

    @SuppressWarnings("unchecked")
    private <K, V> MultiLevelCache<K, V> getCache(String name) {
        return (MultiLevelCache<K, V>) caches.get(name);
    }

    @SuppressWarnings("unchecked")
    private <K, V> MultiLevelSetCache<K, V> getSetCache(String name) {
        return (MultiLevelSetCache<K, V>) setCaches.get(name);
    }

    /**
     * 尝试恢复所有已降级缓存的 Redis L2 访问。
     *
     * <p>这里只扫描降级集合，不遍历所有缓存实例，避免定时任务在缓存实例很多时产生额外开销。</p>
     *
     * @return 本次成功恢复的缓存实例数量
     */
    public int tryRecoverAll() {
        int recovered = 0;
        for (String cacheName : Set.copyOf(degradedCaches)) {
            MultiLevelCache<?, ?> cache = caches.get(cacheName);
            if (cache == null || !cache.isL2Degraded()) {
                degradedCaches.remove(cacheName);
            } else if (cache.tryRecoverL2()) {
                degradedCaches.remove(cacheName);
                recovered++;
            }
        }
        for (String cacheName : Set.copyOf(degradedSetCaches)) {
            MultiLevelSetCache<?, ?> cache = setCaches.get(cacheName);
            if (cache == null || !cache.isL2Degraded()) {
                degradedSetCaches.remove(cacheName);
            } else if (cache.tryRecoverL2()) {
                degradedSetCaches.remove(cacheName);
                recovered++;
            }
        }
        return recovered;
    }

    /**
     * 当前等待恢复的降级缓存数量，主要用于监控和测试。
     */
    public int degradedCacheCount() {
        return degradedCaches.size() + degradedSetCaches.size();
    }
}
