package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.exception.CacheException;
import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.tool.redis.RedisUtil;

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
 *     <li>按缓存名称创建并复用 Set、List、Hash、ZSet 原生结构缓存实例。</li>
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
    /** List 缓存注册表：缓存名称 -> 缓存实例。 */
    private final Map<String, MultiLevelListCache<?, ?>> listCaches = new ConcurrentHashMap<>();
    /** Hash 缓存注册表：缓存名称 -> 缓存实例。 */
    private final Map<String, MultiLevelHashCache<?, ?, ?>> hashCaches = new ConcurrentHashMap<>();
    /** ZSet 缓存注册表：缓存名称 -> 缓存实例。 */
    private final Map<String, MultiLevelZSetCache<?, ?>> zSetCaches = new ConcurrentHashMap<>();
    /** 缓存名称 -> 数据结构类型；同时作为注册和移除操作的互斥锁。 */
    private final Map<String, CacheKind> cacheKinds = new ConcurrentHashMap<>();
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
    /** 已经进入 L2 降级状态的 List 缓存名称。 */
    private final Set<String> degradedListCaches = ConcurrentHashMap.newKeySet();
    /** 已经进入 L2 降级状态的 Hash 缓存名称。 */
    private final Set<String> degradedHashCaches = ConcurrentHashMap.newKeySet();
    /** 已经进入 L2 降级状态的 ZSet 缓存名称。 */
    private final Set<String> degradedZSetCaches = ConcurrentHashMap.newKeySet();

    /**
     * 兼容旧版本的构造器。只要传入 RedisUtil，就默认启用 L2；否则为 L1-only。
     *
     * @param redisUtil Redis 操作入口；为 {@code null} 时仅启用 L1
     * @param serializer KV 缓存值序列化器
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
        synchronized (cacheKinds) {
            registerCacheKind(config.getName(), CacheKind.KV);
            return (MultiLevelCache<K, V>) caches.computeIfAbsent(
                    config.getName(),
                    name -> createCache(config)
            );
        }
    }

    /**
     * 创建 KV 缓存实例。创建前会先合并全局配置，避免调用方手动处理 L1/L2 总开关。
     */
    private <K, V> MultiLevelCache<K, V> createCache(CacheConfig<K, V> config) {
        CacheConfig<K, V> effectiveConfig = effectiveConfig(config);
        return new MultiLevelCache<>(
                effectiveConfig,
                redisUtil,
                serializer,
                invalidationPublisher,
                instanceId,
                () -> degradedCaches.add(effectiveConfig.getName()));
    }

    /**
     * 获取或创建 String key 的 Set 缓存。
     *
     * <p>成员类型优先使用 {@link CacheConfig#getValueType()}，未配置时保留
     * RedisTemplate 的实际反序列化类型，不再假设业务成员一定是 Long。</p>
     *
     * @param config Set 缓存配置
     * @param <V> Set 成员类型
     * @return 已存在或新创建的 Set 缓存
     */
    @SuppressWarnings("unchecked")
    public <V> MultiLevelSetCache<String, V> getOrCreateSetCache(CacheConfig<String, V> config) {
        return (MultiLevelSetCache<String, V>) getOrCreateSetCache(config, Function.identity(), null);
    }

    /**
     * 获取或创建自定义 key 类型的 Set 缓存。
     *
     * @param config Set 缓存配置
     * @param keySerializer 负责把业务 key 转成 Redis key 后缀，也用于失效广播中的 key 表示
     * @param <K> 业务 key 类型
     * @param <V> Set 成员类型
     * @return 已存在或新创建的 Set 缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelSetCache<K, V> getOrCreateSetCache(CacheConfig<K, V> config,
                                                                Function<K, String> keySerializer) {
        return (MultiLevelSetCache<K, V>) getOrCreateSetCache(config, keySerializer, null);
    }

    /**
     * 获取或创建 Set 缓存，并显式指定成员类型。
     *
     * <p>显式类型优先级高于 {@link CacheConfig#getValueType()}；两者均未配置时保留
     * RedisTemplate 的实际反序列化类型。</p>
     *
     * @param config Set 缓存配置
     * @param keySerializer 业务 key 序列化函数
     * @param memberType Redis 成员的预期类型；可为 {@code null}
     * @param <K> 业务 key 类型
     * @param <V> Set 成员类型
     * @return 已存在或新创建的 Set 缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelSetCache<K, V> getOrCreateSetCache(CacheConfig<K, V> config,
                                                                Function<K, String> keySerializer,
                                                                Class<V> memberType) {
        synchronized (cacheKinds) {
            registerCacheKind(config.getName(), CacheKind.SET);
            return (MultiLevelSetCache<K, V>) setCaches.computeIfAbsent(
                    config.getName(),
                    name -> createSetCache(
                            config,
                            keySerializer,
                            memberType
                    )
            );
        }
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
     * 获取或创建 String key 的 List 缓存。
     *
     * <p>元素类型优先使用 {@link CacheConfig#getValueType()}；未配置时保留 RedisTemplate
     * 的实际反序列化类型。</p>
     *
     * @param config List 缓存配置
     * @param <V> List 元素类型
     * @return 已存在或新创建的 List 缓存
     */
    @SuppressWarnings("unchecked")
    public <V> MultiLevelListCache<String, V> getOrCreateListCache(CacheConfig<String, V> config) {
        return (MultiLevelListCache<String, V>) getOrCreateListCache(config, Function.identity(), null);
    }

    /**
     * 获取或创建自定义 key 类型的 List 缓存。
     *
     * @param config List 缓存配置
     * @param keySerializer 负责把业务 key 转成 Redis key 后缀，也用于失效广播中的 key 表示
     * @param <K> 业务 key 类型
     * @param <V> List 元素类型
     * @return 已存在或新创建的 List 缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelListCache<K, V> getOrCreateListCache(CacheConfig<K, V> config,
                                                                  Function<K, String> keySerializer) {
        return (MultiLevelListCache<K, V>) getOrCreateListCache(config, keySerializer, null);
    }

    /**
     * 获取或创建 List 缓存，并显式指定元素类型。
     *
     * @param config List 缓存配置
     * @param keySerializer 业务 key 序列化函数
     * @param elementType Redis 元素的预期类型；可为 {@code null}
     * @param <K> 业务 key 类型
     * @param <V> List 元素类型
     * @return 已存在或新创建的 List 缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelListCache<K, V> getOrCreateListCache(CacheConfig<K, V> config,
                                                                  Function<K, String> keySerializer,
                                                                  Class<V> elementType) {
        synchronized (cacheKinds) {
            registerCacheKind(config.getName(), CacheKind.LIST);
            return (MultiLevelListCache<K, V>) listCaches.computeIfAbsent(
                    config.getName(),
                    name -> createListCache(
                            config,
                            keySerializer,
                            elementType
                    )
            );
        }
    }

    /**
     * 创建 List 缓存实例，并把首次降级回调接入管理器的待恢复集合。
     */
    private <K, V> MultiLevelListCache<K, V> createListCache(CacheConfig<K, V> config,
                                                              Function<K, String> keySerializer,
                                                              Class<V> elementType) {
        CacheConfig<K, V> effectiveConfig = effectiveConfig(config);
        return new MultiLevelListCache<>(
                effectiveConfig,
                redisUtil,
                keySerializer,
                elementType,
                invalidationPublisher,
                instanceId,
                () -> degradedListCaches.add(effectiveConfig.getName()));
    }

    /**
     * 获取或创建 Hash 缓存。
     *
     * @param config Hash 缓存配置
     * @param keySerializer 负责把业务 key 转成 Redis key 后缀，也用于失效广播中的 key 表示
     * @param hashKeyType Hash field 的目标类型；传 null 时按 RedisTemplate 返回值直接强转
     * @param hashValueType Hash value 的目标类型；传 null 时回退到配置的 value 类型
     * @param <K> 业务 key 类型
     * @param <HK> Hash field 类型
     * @param <HV> Hash value 类型
     * @return 已存在或新创建的 Hash 缓存
     */
    @SuppressWarnings("unchecked")
    public <K, HK, HV> MultiLevelHashCache<K, HK, HV> getOrCreateHashCache(CacheConfig<K, HV> config,
                                                                            Function<K, String> keySerializer,
                                                                            Class<HK> hashKeyType,
                                                                            Class<HV> hashValueType) {
        synchronized (cacheKinds) {
            registerCacheKind(config.getName(), CacheKind.HASH);
            return (MultiLevelHashCache<K, HK, HV>)
                    hashCaches.computeIfAbsent(
                            config.getName(),
                            name -> createHashCache(
                                    config,
                                    keySerializer,
                                    hashKeyType,
                                    hashValueType
                            )
                    );
        }
    }

    /**
     * 创建 Hash 缓存实例，并把首次降级回调接入管理器的待恢复集合。
     */
    private <K, HK, HV> MultiLevelHashCache<K, HK, HV> createHashCache(CacheConfig<K, HV> config,
                                                                        Function<K, String> keySerializer,
                                                                        Class<HK> hashKeyType,
                                                                        Class<HV> hashValueType) {
        CacheConfig<K, HV> effectiveConfig = effectiveConfig(config);
        return new MultiLevelHashCache<>(
                effectiveConfig,
                redisUtil,
                keySerializer,
                hashKeyType,
                hashValueType,
                invalidationPublisher,
                instanceId,
                () -> degradedHashCaches.add(effectiveConfig.getName()));
    }

    /**
     * 获取或创建 String key 的 ZSet 缓存。
     *
     * <p>成员类型优先使用 {@link CacheConfig#getValueType()}；未配置时保留 RedisTemplate
     * 的实际反序列化类型。</p>
     *
     * @param config ZSet 缓存配置
     * @param <V> ZSet 成员类型
     * @return 已存在或新创建的 ZSet 缓存
     */
    @SuppressWarnings("unchecked")
    public <V> MultiLevelZSetCache<String, V> getOrCreateZSetCache(CacheConfig<String, V> config) {
        return (MultiLevelZSetCache<String, V>) getOrCreateZSetCache(config, Function.identity(), null);
    }

    /**
     * 获取或创建自定义 key 类型的 ZSet 缓存。
     *
     * @param config ZSet 缓存配置
     * @param keySerializer 负责把业务 key 转成 Redis key 后缀，也用于失效广播中的 key 表示
     * @param <K> 业务 key 类型
     * @param <V> ZSet 成员类型
     * @return 已存在或新创建的 ZSet 缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelZSetCache<K, V> getOrCreateZSetCache(CacheConfig<K, V> config,
                                                                  Function<K, String> keySerializer) {
        return (MultiLevelZSetCache<K, V>) getOrCreateZSetCache(config, keySerializer, null);
    }

    /**
     * 获取或创建 ZSet 缓存，并显式指定成员类型。
     *
     * @param config ZSet 缓存配置
     * @param keySerializer 业务 key 序列化函数
     * @param memberType Redis 成员的预期类型；可为 {@code null}
     * @param <K> 业务 key 类型
     * @param <V> ZSet 成员类型
     * @return 已存在或新创建的 ZSet 缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelZSetCache<K, V> getOrCreateZSetCache(CacheConfig<K, V> config,
                                                                  Function<K, String> keySerializer,
                                                                  Class<V> memberType) {
        synchronized (cacheKinds) {
            registerCacheKind(config.getName(), CacheKind.ZSET);
            return (MultiLevelZSetCache<K, V>)
                    zSetCaches.computeIfAbsent(
                            config.getName(),
                            name -> createZSetCache(
                                    config,
                                    keySerializer,
                                    memberType
                            )
                    );
        }
    }

    /**
     * 创建 ZSet 缓存实例，并把首次降级回调接入管理器的待恢复集合。
     */
    private <K, V> MultiLevelZSetCache<K, V> createZSetCache(CacheConfig<K, V> config,
                                                              Function<K, String> keySerializer,
                                                              Class<V> memberType) {
        CacheConfig<K, V> effectiveConfig = effectiveConfig(config);
        return new MultiLevelZSetCache<>(
                effectiveConfig,
                redisUtil,
                keySerializer,
                memberType,
                invalidationPublisher,
                instanceId,
                () -> degradedZSetCaches.add(effectiveConfig.getName()));
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
                .consistencyMode(config.getConsistencyMode())
                .readValidation(config.getReadValidation())
                .writePolicy(config.getWritePolicy())
                .nullValueCache(config.isNullValueCache())
                .nullValueTtl(config.getNullValueTtl())
                .valueType(config.getValueType())
                .keySerializer(config::serializeKey)
                .redisKeyPrefix(prefix)
                .build();
    }

    /**
     * 获取已经注册过的 KV 缓存。
     *
     * @param name 缓存区域名称
     * @param <K> 缓存 key 类型
     * @param <V> 缓存 value 类型
     * @return 已注册的 KV 缓存
     * @throws CacheException 如果缓存尚未通过 {@link #getOrCreate(CacheConfig)} 创建
     */
    @SuppressWarnings("unchecked")
    public <K, V> MultiLevelCache<K, V> get(String name) {
        MultiLevelCache<?, ?> cache = caches.get(name);
        if (cache == null) {
            throw CacheException.cacheNotFound();
        }
        return (MultiLevelCache<K, V>) cache;
    }

    /**
     * 从管理器中移除缓存实例，并清理降级记录。
     *
     * @param name 缓存区域名称
     */
    public void remove(String name) {
        synchronized (cacheKinds) {
            caches.remove(name);
            setCaches.remove(name);
            listCaches.remove(name);
            hashCaches.remove(name);
            zSetCaches.remove(name);
            degradedCaches.remove(name);
            degradedSetCaches.remove(name);
            degradedListCaches.remove(name);
            degradedHashCaches.remove(name);
            degradedZSetCaches.remove(name);
            cacheKinds.remove(name);
        }
    }

    /**
     * 返回所有已经注册的 KV 缓存实例。
     *
     * @return KV 缓存实例视图
     */
    public Collection<MultiLevelCache<?, ?>> getAll() {
        return caches.values();
    }

    /**
     * 当前 JVM 缓存节点 ID。
     *
     * @return 当前 JVM 缓存节点唯一标识
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * 仅清理当前 JVM 的某个 L1 条目。
     *
     * <p>该方法供失效监听器调用，不删除 Redis，也不会再次广播，避免形成广播循环。</p>
     *
     * @param cacheName 缓存区域名称
     * @param key 已序列化为字符串的业务 key
     */
    public void evictLocal(String cacheName, String key) {
        MultiLevelCache<?, ?> cache = caches.get(cacheName);
        if (cache != null) {
            cache.evictLocalSerializedKey(key);
            return;
        }
        MultiLevelSetCache<?, ?> setCache = setCaches.get(cacheName);
        if (setCache != null) {
            setCache.evictLocalSerializedKey(key);
            return;
        }
        MultiLevelListCache<?, ?> listCache = listCaches.get(cacheName);
        if (listCache != null) {
            listCache.evictLocalSerializedKey(key);
            return;
        }
        MultiLevelHashCache<?, ?, ?> hashCache = hashCaches.get(cacheName);
        if (hashCache != null) {
            hashCache.evictLocalSerializedKey(key);
            return;
        }
        MultiLevelZSetCache<?, ?> zSetCache = zSetCaches.get(cacheName);
        if (zSetCache != null) {
            zSetCache.evictLocalSerializedKey(key);
        }
    }

    /**
     * 仅清空当前 JVM 的某个缓存区域 L1。
     *
     * @param cacheName 缓存区域名称
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
            return;
        }
        MultiLevelListCache<?, ?> listCache = listCaches.get(cacheName);
        if (listCache != null) {
            listCache.evictLocalAll();
            return;
        }
        MultiLevelHashCache<?, ?, ?> hashCache = hashCaches.get(cacheName);
        if (hashCache != null) {
            hashCache.evictLocalAll();
            return;
        }
        MultiLevelZSetCache<?, ?> zSetCache = zSetCaches.get(cacheName);
        if (zSetCache != null) {
            zSetCache.evictLocalAll();
        }
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
        for (String cacheName : Set.copyOf(degradedListCaches)) {
            MultiLevelListCache<?, ?> cache = listCaches.get(cacheName);
            if (cache == null || !cache.isL2Degraded()) {
                degradedListCaches.remove(cacheName);
            } else if (cache.tryRecoverL2()) {
                degradedListCaches.remove(cacheName);
                recovered++;
            }
        }
        for (String cacheName : Set.copyOf(degradedHashCaches)) {
            MultiLevelHashCache<?, ?, ?> cache = hashCaches.get(cacheName);
            if (cache == null || !cache.isL2Degraded()) {
                degradedHashCaches.remove(cacheName);
            } else if (cache.tryRecoverL2()) {
                degradedHashCaches.remove(cacheName);
                recovered++;
            }
        }
        for (String cacheName : Set.copyOf(degradedZSetCaches)) {
            MultiLevelZSetCache<?, ?> cache = zSetCaches.get(cacheName);
            if (cache == null || !cache.isL2Degraded()) {
                degradedZSetCaches.remove(cacheName);
            } else if (cache.tryRecoverL2()) {
                degradedZSetCaches.remove(cacheName);
                recovered++;
            }
        }
        return recovered;
    }

    /**
     * 当前等待恢复的降级缓存数量，主要用于监控和测试。
     *
     * @return 待恢复缓存实例总数
     */
    public int degradedCacheCount() {
        return degradedCaches.size()
                + degradedSetCaches.size()
                + degradedListCaches.size()
                + degradedHashCaches.size()
                + degradedZSetCaches.size();
    }

    /**
     * 为缓存名称登记唯一的数据结构类型。
     *
     * @param name 缓存区域名称
     * @param kind 待登记的数据结构类型
     * @throws CacheException 名称已经被其它数据结构占用时抛出
     */
    private void registerCacheKind(String name, CacheKind kind) {
        CacheKind existing = cacheKinds.putIfAbsent(name, kind);
        if (existing != null && existing != kind) {
            throw CacheException.cacheTypeConflict();
        }
    }

    /**
     * 管理器支持的缓存数据结构类型。
     */
    private enum CacheKind {
        /** 普通 key/value 缓存。 */
        KV,
        /** Redis Set 缓存。 */
        SET,
        /** Redis List 缓存。 */
        LIST,
        /** Redis Hash 缓存。 */
        HASH,
        /** Redis ZSet 缓存。 */
        ZSET
    }
}
