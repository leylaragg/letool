package io.github.leylaragg.letool.redis;

import io.github.leylaragg.letool.lock.core.LockRequest;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.redis.cache.RedisCachePolicy;
import io.github.leylaragg.letool.redis.cache.RedisCacheTemplate;
import io.github.leylaragg.letool.redis.config.LetoolRedisProperties;
import io.github.leylaragg.letool.tool.util.JsonUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 面向业务的 Redis 操作门面，底层基于 {@link RedisTemplate}。
 *
 * <h2>设计说明</h2>
 * <p>本门面不内置 JSON 序列化器，也不强制创建默认 {@code RedisTemplate}。
 * 应用侧通常会配置自己的 key/value/hash 序列化方案，例如 Fastjson2、Jackson 或 JDK 序列化。
 * {@code RedisFacade} 只负责把值交给 {@code RedisTemplate}，从而复用应用已有的序列化配置。</p>
 *
 * <h2>自动装配</h2>
 * <p>starter 只在应用上下文中存在名为 {@code redisTemplate} 的对象模板时创建本门面。
 * 只有 {@code StringRedisTemplate} 时不会自动创建，避免误以为对象序列化可用。</p>
 *
 * <h2>支持的操作类型</h2>
 * <ul>
 *   <li><b>Key</b>：存在判断、删除、过期时间设置与查询</li>
 *   <li><b>Value</b>：set/get/increment，支持应用 RedisTemplate 序列化后的任意对象</li>
 *   <li><b>Hash</b>：hset/hget/hgetAll/hdel</li>
 *   <li><b>List</b>：lpush/rpush/lpop/rpop/lrange</li>
 *   <li><b>Set</b>：sadd/smembers/sismember</li>
 *   <li><b>ZSet</b>：zadd/zrange</li>
 *   <li><b>Lua 脚本</b>：executeScript</li>
 *   <li><b>管道</b>：pipeline 批量操作</li>
 *   <li><b>分布式锁</b>：获取 Redisson 原生锁，或在自动释放的锁内执行回调</li>
 *   <li><b>缓存回源</b>：分布式互斥、锁内双检、空值哨兵和 TTL 抖动</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 字符串：RedisTemplate 反序列化后按调用方声明类型返回
 * redisFacade.set("user:name", "张三", Duration.ofHours(1));
 * String name = redisFacade.get("user:name");
 *
 * // 对象：由应用配置的 RedisTemplate 序列化器负责序列化和反序列化
 * redisFacade.set("user:1", user, Duration.ofHours(1));
 * User cachedUser = redisFacade.get("user:1", User.class);
 *
 * // 兼容旧对象方法名
 * redisFacade.setObject("user:2", user, Duration.ofHours(1));
 * User user2 = redisFacade.getObject("user:2", User.class);
 *
 * // Hash
 * redisFacade.hset("user:1", "name", "张三");
 * Map<String, String> all = redisFacade.hgetAll("user:1");
 *
 * // Lua 脚本
 * String script = "return redis.call('GET', KEYS[1])";
 * String result = redisFacade.executeScript(script, List.of("key1"));
 * }</pre>
 */
public class RedisFacade {

    /**
     * Lua ARGV 序列化器：预序列化业务值保持原字节，其它元数据统一写成 UTF-8 字符串。
     */
    private static final RedisSerializer<Object> RAW_SCRIPT_ARGUMENT_SERIALIZER =
            new RedisSerializer<>() {
                @Override
                public byte[] serialize(Object value) throws SerializationException {
                    if (value instanceof byte[] bytes) {
                        return bytes;
                    }
                    return StringRedisSerializer.UTF_8.serialize(value == null ? null : value.toString());
                }

                @Override
                public Object deserialize(byte[] bytes) throws SerializationException {
                    return StringRedisSerializer.UTF_8.deserialize(bytes);
                }
            };

    /** 应用侧配置好的 RedisTemplate，value/hashValue 序列化方案由应用决定。 */
    private final RedisTemplate<String, Object> redisTemplate;

    /** Redisson 高级锁入口；仅基础命令构造方式下允许为空。 */
    private final RedissonClient redissonClient;

    /** 自动释放分布式锁的模板；仅基础命令构造方式下允许为空。 */
    private final LockTemplate lockTemplate;

    /** 带击穿和穿透保护的缓存回源模板；仅基础命令构造方式下允许为空。 */
    private final RedisCacheTemplate cacheTemplate;

    /** Redis Starter 默认策略。 */
    private final LetoolRedisProperties redisProperties;

    /**
     * 创建只提供基础命令的 Redis 门面。
     *
     * @param redisTemplate 应用侧对象 RedisTemplate
     */
    public RedisFacade(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, null, null, null, new LetoolRedisProperties());
    }

    /**
     * 创建完整 Redis 业务门面。
     *
     * @param redisTemplate 对象 Redis 模板
     * @param redissonClient Redisson 客户端；没有锁后端时允许为空
     * @param lockTemplate 自动释放锁的模板；没有锁后端时允许为空
     * @param cacheTemplate 缓存回源模板；没有锁后端时允许为空
     * @param redisProperties Redis Starter 默认策略
     */
    public RedisFacade(
            RedisTemplate<String, Object> redisTemplate,
            RedissonClient redissonClient,
            LockTemplate lockTemplate,
            RedisCacheTemplate cacheTemplate,
            LetoolRedisProperties redisProperties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.redissonClient = redissonClient;
        this.lockTemplate = lockTemplate;
        this.cacheTemplate = cacheTemplate;
        this.redisProperties = Objects.requireNonNull(
                redisProperties, "redisProperties must not be null");
    }

    /**
     * 获取底层 RedisTemplate，用于调用门面未封装的原生 Redis 操作。
     *
     * @return RedisTemplate 实例
     */
    public RedisTemplate<String, Object> getTemplate() {
        return redisTemplate;
    }

    /**
     * 获取 Redisson 原生锁，供高级控制需求直接使用。
     *
     * <p>普通业务优先使用 {@link #executeWithLock(String, Supplier)}，由模板保证释放。</p>
     *
     * @param key 业务锁 key
     * @return 已应用 Starter 前缀与公平性配置的 Redisson 锁
     */
    public RLock getLock(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("锁 key 不能为空");
        }
        RedissonClient client = requireRedissonClient();
        String fullKey = redisProperties.getLock().getKeyPrefix() + key;
        return redisProperties.getLock().isFair()
                ? client.getFairLock(fullKey)
                : client.getLock(fullKey);
    }

    /**
     * 使用默认锁等待时间和 Redisson 看门狗执行业务回调。
     *
     * @param key 业务锁 key
     * @param supplier 锁内业务回调
     * @param <T> 业务返回类型
     * @return 业务回调结果
     */
    public <T> T executeWithLock(String key, Supplier<T> supplier) {
        return requireLockTemplate().execute(
                LockRequest.watchdog(key, redisProperties.getCache().getLockWait()), supplier);
    }

    /**
     * 使用默认锁等待时间执行无返回值业务回调。
     *
     * @param key 业务锁 key
     * @param runnable 锁内业务回调
     */
    public void executeWithLock(String key, Runnable runnable) {
        requireLockTemplate().execute(
                LockRequest.watchdog(key, redisProperties.getCache().getLockWait()), runnable);
    }

    /**
     * 使用 Starter 默认空值、抖动和锁等待配置读取或回源。
     *
     * @param key 缓存 key
     * @param type 业务值类型
     * @param ttl 正常数据 TTL
     * @param loader 数据源回调
     * @param <T> 业务值类型
     * @return 缓存值或数据源结果
     */
    public <T> T getOrLoad(
            String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return requireCacheTemplate().getOrLoad(
                key, type, defaultPolicy(ttl), loader);
    }

    /**
     * 使用调用方策略读取或回源。
     *
     * @param key 缓存 key
     * @param type 业务值类型
     * @param policy 本次缓存策略
     * @param loader 数据源回调
     * @param <T> 业务值类型
     * @return 缓存值或数据源结果
     */
    public <T> T getOrLoad(
            String key,
            Class<T> type,
            RedisCachePolicy<T> policy,
            Supplier<T> loader) {
        return requireCacheTemplate().getOrLoad(key, type, policy, loader);
    }

    /**
     * 获取 Redis Value 原生操作视图。
     *
     * <p>读写值都会直接使用应用配置的 RedisTemplate value serializer，不做二次 JSON 处理或字符串转换。</p>
     *
     * @return Value 原生操作视图
     */
    public ValueOperations<String, Object> opsForValue() {
        return redisTemplate.opsForValue();
    }

    /**
     * 获取绑定到指定 key 的 Redis Value 原生操作视图。
     *
     * <p>适合缓存层在已拼好 Redis key 后直接调用 get/set/increment 等操作。</p>
     *
     * @param key Redis key
     * @return 绑定 key 的 Value 操作视图
     */
    public BoundValueOperations<String, Object> boundValueOps(String key) {
        return redisTemplate.boundValueOps(key);
    }

    /**
     * 获取 Redis List 原生操作视图。
     *
     * <p>List 元素会逐条使用 RedisTemplate value serializer 序列化，适合真实 Redis List 结构。</p>
     *
     * @return List 原生操作视图
     */
    public ListOperations<String, Object> opsForList() {
        return redisTemplate.opsForList();
    }

    /**
     * 获取绑定到指定 key 的 Redis List 原生操作视图。
     *
     * @param key Redis key
     * @return 绑定 key 的 List 操作视图
     */
    public BoundListOperations<String, Object> boundListOps(String key) {
        return redisTemplate.boundListOps(key);
    }

    /**
     * 获取 Redis Set 原生操作视图。
     *
     * <p>Set 成员保持对象形态交给 RedisTemplate serializer 处理，不会先转成 String。</p>
     *
     * @return Set 原生操作视图
     */
    public SetOperations<String, Object> opsForSet() {
        return redisTemplate.opsForSet();
    }

    /**
     * 获取绑定到指定 key 的 Redis Set 原生操作视图。
     *
     * @param key Redis key
     * @return 绑定 key 的 Set 操作视图
     */
    public BoundSetOperations<String, Object> boundSetOps(String key) {
        return redisTemplate.boundSetOps(key);
    }

    /**
     * 获取 Redis ZSet 原生操作视图。
     *
     * <p>member 使用 RedisTemplate serializer，score 使用 Redis 原生 double 分数。</p>
     *
     * @return ZSet 原生操作视图
     */
    public ZSetOperations<String, Object> opsForZSet() {
        return redisTemplate.opsForZSet();
    }

    /**
     * 获取绑定到指定 key 的 Redis ZSet 原生操作视图。
     *
     * @param key Redis key
     * @return 绑定 key 的 ZSet 操作视图
     */
    public BoundZSetOperations<String, Object> boundZSetOps(String key) {
        return redisTemplate.boundZSetOps(key);
    }

    /**
     * 获取 Redis Hash 原生操作视图。
     *
     * <p>Hash field/value 分别使用 RedisTemplate 的 hashKey/hashValue serializer。</p>
     *
     * @return Hash 原生操作视图
     */
    public HashOperations<String, Object, Object> opsForHash() {
        return redisTemplate.opsForHash();
    }

    /**
     * 获取绑定到指定 key 的 Redis Hash 原生操作视图。
     *
     * @param key Redis key
     * @return 绑定 key 的 Hash 操作视图
     */
    public BoundHashOperations<String, Object, Object> boundHashOps(String key) {
        return redisTemplate.boundHashOps(key);
    }

    // ======================== Key 操作 ========================

    /**
     * 判断 key 是否存在。
     *
     * @param key Redis key
     * @return {@code true} 表示存在
     */
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 删除单个 key。
     *
     * @param key Redis key
     * @return {@code true} 表示删除成功
     */
    public boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 批量删除 key。
     *
     * @param keys Redis key 集合
     * @return 实际删除的 key 数量
     */
    public long delete(Collection<String> keys) {
        Long count = redisTemplate.delete(keys);
        return count;
    }

    /**
     * 设置 key 的过期时间。
     *
     * @param key     Redis key
     * @param timeout 过期时长
     * @param unit    时间单位
     * @return {@code true} 表示设置成功
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取 key 的剩余过期时间。
     *
     * @param key  Redis key
     * @param unit 时间单位
     * @return 剩余时间；Redis 返回 {@code null} 时统一返回 -1
     */
    public long getExpire(String key, TimeUnit unit) {
        Long ttl = redisTemplate.getExpire(key, unit);
        return ttl;
    }

    // ======================== Value 操作 ========================

    /**
     * 写入任意对象值，永不过期。
     *
     * @param key   Redis key
     * @param value 任意对象值，序列化由 RedisTemplate 决定
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 写入字符串值，永不过期。保留该重载用于兼容旧版本调用。
     *
     * @param key   Redis key
     * @param value 字符串值
     */
    public void set(String key, String value) {
        set(key, (Object) value);
    }

    /**
     * 写入任意对象值，并设置过期时间。
     *
     * @param key      Redis key
     * @param value    任意对象值，序列化由 RedisTemplate 决定
     * @param duration 过期时长
     */
    public void set(String key, Object value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    /**
     * 写入字符串值，并设置过期时间。保留该重载用于兼容旧版本调用。
     *
     * @param key      Redis key
     * @param value    字符串值
     * @param duration 过期时长
     */
    public void set(String key, String value, Duration duration) {
        set(key, (Object) value, duration);
    }

    /**
     * 写入任意对象值，并设置过期时间。
     *
     * @param key     Redis key
     * @param value   任意对象值，序列化由 RedisTemplate 决定
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 写入字符串值，并设置过期时间。保留该重载用于兼容旧版本调用。
     *
     * @param key     Redis key
     * @param value   字符串值
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        set(key, (Object) value, timeout, unit);
    }

    /**
     * 读取 RedisTemplate 反序列化后的值。
     *
     * <p>该方法不做字符串转换。RedisTemplate 会先通过 value serializer 反序列化，
     * 本方法再按调用方声明的泛型返回该 Java 对象。需要显式类型转换时可使用
     * {@link #get(String, Class)}。</p>
     *
     * @param key Redis key
     * @param <T> 调用方期望的返回类型
     * @return 反序列化后的值；key 不存在时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 读取指定类型的对象值。
     *
     * <p>正常情况下，RedisTemplate 会通过配置好的 value serializer 直接反序列化出目标对象。
     * 如果历史数据是 JSON 字符串，本方法也会按目标类型做一次 JSON 转换。</p>
     *
     * @param key   Redis key
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 目标类型对象；key 不存在时返回 {@code null}
     */
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        return convertValue(value, clazz);
    }

    /**
     * 对数值执行自增或自减。
     *
     * @param key   Redis key
     * @param delta 增量，可为负数
     * @return 自增后的值
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 写入对象值的兼容方法。
     *
     * <p>旧版本方法名保留为 {@code setObject}，但不再在门面内部强制 JSON 序列化，
     * 而是直接交给 RedisTemplate 的序列化器处理。</p>
     *
     * @param key      Redis key
     * @param obj      对象值
     * @param duration 过期时长
     * @param <T>      对象类型
     */
    public <T> void setObject(String key, T obj, Duration duration) {
        set(key, obj, duration);
    }

    /**
     * 读取对象值的兼容方法。
     *
     * @param key   Redis key
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 目标类型对象；key 不存在时返回 {@code null}
     */
    public <T> T getObject(String key, Class<T> clazz) {
        return get(key, clazz);
    }

    // ======================== Hash 操作 ========================

    /**
     * 设置 Hash 字段值。
     *
     * @param key   Redis key
     * @param field Hash 字段
     * @param value 字段值
     */
    public void hset(String key, Object field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * 获取 Hash 字段值。
     *
     * @param key   Redis key
     * @param field Hash 字段
     * @param <T> 字段值类型
     * @return 字段值；字段不存在时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T hget(String key, Object field) {
        return (T) redisTemplate.opsForHash().get(key, field);
    }

    /**
     * 获取 Hash 中全部字段和值。
     *
     * @param key Redis key
     * @param <HK> Hash 字段类型
     * @param <HV> Hash 值类型
     * @return field 到 value 的映射
     */
    @SuppressWarnings("unchecked")
    public <HK, HV> Map<HK, HV> hgetAll(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<HK, HV> result = new LinkedHashMap<>();
        entries.forEach((k, v) -> result.put((HK) k, (HV) v));
        return result;
    }

    /**
     * 删除 Hash 中的指定字段。
     *
     * @param key    Redis key
     * @param fields 字段名列表
     * @return 实际删除的字段数量
     */
    public long hdel(String key, String... fields) {
        return redisTemplate.opsForHash().delete(key, (Object[]) fields);
    }

    // ======================== List 操作 ========================

    /**
     * 从左侧推入列表。
     *
     * @param key   Redis key
     * @param value 元素值
     * @return 推入后的列表长度
     */
    public long lpush(String key, Object value) {
        Long size = redisTemplate.opsForList().leftPush(key, value);
        return size == null ? 0 : size;
    }

    /**
     * 从右侧推入列表。
     *
     * @param key   Redis key
     * @param value 元素值
     * @return 推入后的列表长度
     */
    public long rpush(String key, Object value) {
        Long size = redisTemplate.opsForList().rightPush(key, value);
        return size == null ? 0 : size;
    }

    /**
     * 从左侧弹出列表元素。
     *
     * @param key Redis key
     * @param <T> 元素类型
     * @return 弹出的元素；列表为空时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T lpop(String key) {
        return (T) redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从右侧弹出列表元素。
     *
     * @param key Redis key
     * @param <T> 元素类型
     * @return 弹出的元素；列表为空时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T rpop(String key) {
        return (T) redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 获取列表指定范围内的元素。
     *
     * @param key   Redis key
     * @param start 起始索引，包含
     * @param end   结束索引，包含；-1 表示最后一个元素
     * @param <T> 元素类型
     * @return 元素列表；Redis 返回 {@code null} 时返回空列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> lrange(String key, long start, long end) {
        List<Object> values = redisTemplate.opsForList().range(key, start, end);
        if (values == null) {
            return Collections.emptyList();
        }
        return (List<T>) values;
    }

    // ======================== Set 操作 ========================

    /**
     * 添加元素到 Set。
     *
     * @param key    Redis key
     * @param values 元素值
     * @return 成功添加的元素数量
     */
    public long sadd(String key, Object... values) {
        Long count = redisTemplate.opsForSet().add(key, values);
        return count == null ? 0 : count;
    }

    /**
     * 获取 Set 全部成员。
     *
     * @param key Redis key
     * @param <T> 成员类型
     * @return Set 成员；Redis 返回 {@code null} 时返回空集合
     */
    @SuppressWarnings("unchecked")
    public <T> Set<T> smembers(String key) {
        Set<Object> values = redisTemplate.opsForSet().members(key);
        if (values == null) {
            return Collections.emptySet();
        }
        return (Set<T>) values;
    }

    /**
     * 判断元素是否为 Set 成员。
     *
     * @param key   Redis key
     * @param value 元素值
     * @return {@code true} 表示存在
     */
    public boolean sismember(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    // ======================== ZSet 操作 ========================

    /**
     * 添加元素到有序集合。
     *
     * @param key   Redis key
     * @param value 元素值
     * @param score 分数
     * @return {@code true} 表示添加成功
     */
    public boolean zadd(String key, Object value, double score) {
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
    }

    /**
     * 获取有序集合指定排名范围内的元素。
     *
     * @param key   Redis key
     * @param start 起始排名，包含
     * @param end   结束排名，包含；-1 表示最后一个元素
     * @param <T> 元素类型
     * @return 元素集合；Redis 返回 {@code null} 时返回空集合
     */
    @SuppressWarnings("unchecked")
    public <T> Set<T> zrange(String key, long start, long end) {
        Set<Object> values = redisTemplate.opsForZSet().range(key, start, end);
        if (values == null) {
            return Collections.emptySet();
        }
        return (Set<T>) values;
    }

    // ======================== Lua 脚本 ========================

    /**
     * 执行 Lua 脚本。
     *
     * <p>脚本中通过 {@code KEYS[n]} 访问 keys 参数，通过 {@code ARGV[n]} 访问 args 参数。</p>
     *
     * @param script Lua 脚本内容
     * @param keys   KEY 列表
     * @param args   ARGV 参数列表
     * @param <T>    脚本返回值类型
     * @return 脚本执行结果
     */
    @SuppressWarnings("unchecked")
    public <T> T executeScript(String script, List<String> keys, Object... args) {
        DefaultRedisScript<T> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType((Class<T>) Object.class);
        return redisTemplate.execute(redisScript, keys, args);
    }

    /**
     * 执行 Lua 脚本，所有 ARGV 参数使用 {@link StringRedisSerializer} 序列化为纯 UTF-8 字符串。
     *
     * <p>与 {@link #executeScript(String, List, Object...)} 的区别在于，本方法不会经过 value serializer，
     * 因此 args 不会被 JSON 序列化器（如 Fastjson2 的 WriteClassName）包装为 JSON 对象。
     * 适用于 TTL、计数器等必须直接传给 Redis 的元数据参数。</p>
     *
     * <p>对于需要保留 {@code @type} 元数据的业务对象值，调用方应先通过
     * {@link #serializeValue(Object)} 预序列化，再将结果作为 args 传入。</p>
     *
     * @param script Lua 脚本内容
     * @param keys   KEY 列表
     * @param args   ARGV 参数列表，每个参数通过 {@code toString()} 转换为字符串
     * @param <T>    脚本返回值类型
     * @return 脚本执行结果
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T executeScriptRaw(String script, List<String> keys, Object... args) {
        return (T) executeScriptRaw(script, Object.class, keys, args);
    }

    /**
     * 执行使用纯字符串 ARGV 的 Lua 脚本，并显式声明 Redis 返回类型。
     *
     * <p>Spring Data Redis 会根据结果类型选择 Lettuce 输出解码器。整数脚本必须声明
     * {@link Long}，否则 {@link Object} 会被当成普通 Value 解码并在收到整数时失败。</p>
     *
     * @param script Lua 脚本内容
     * @param resultType 脚本返回类型
     * @param keys KEY 列表
     * @param args ARGV 参数列表，每个参数通过 {@code toString()} 转换为字符串
     * @param <T> 脚本返回值类型
     * @return 脚本执行结果
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T executeScriptRaw(
            String script, Class<T> resultType, List<String> keys, Object... args) {
        DefaultRedisScript<T> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Objects.requireNonNull(resultType, "脚本返回类型不能为空"));
        RedisSerializer stringSerializer = RedisSerializer.string();
        return (T) redisTemplate.execute(
                redisScript,
                RAW_SCRIPT_ARGUMENT_SERIALIZER,
                stringSerializer,
                keys,
                args);
    }

    /**
     * 使用当前 RedisTemplate 配置的 value serializer 序列化值。
     *
     * <p>序列化结果可用于 {@link #executeScriptRaw(String, List, Object...)}
     * 的 args 参数，确保值经过正常的序列化流程但不会在脚本参数中被二次包装。</p>
     *
     * @param value 待序列化的值
     * @return 序列化后的字节数组
     */
    @SuppressWarnings("unchecked")
    public byte[] serializeValue(Object value) {
        return ((RedisSerializer<Object>) redisTemplate.getValueSerializer()).serialize(value);
    }

    // ======================== Pipeline ========================

    /**
     * 执行管道批量操作，减少网络往返。
     *
     * @param consumer 管道操作回调，接收 RedisOperations 执行批量命令
     * @return 每条命令的返回值列表
     */
    public List<Object> pipeline(java.util.function.Consumer<RedisOperations<String, Object>> consumer) {
        return redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, Object> redisOperations = (RedisOperations<String, Object>) operations;
                consumer.accept(redisOperations);
                return null;
            }
        });
    }

    /**
     * 将 RedisTemplate 反序列化出的值转换为调用方需要的类型。
     */
    private <T> T convertValue(Object value, Class<T> clazz) {
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        if (value instanceof String stringValue) {
            if (String.class.equals(clazz)) {
                return clazz.cast(stringValue);
            }
            return JsonUtil.parseObject(stringValue, clazz);
        }
        if (String.class.equals(clazz)) {
            return clazz.cast(value.toString());
        }
        return JsonUtil.convert(value, clazz);
    }

    private <T> RedisCachePolicy<T> defaultPolicy(Duration ttl) {
        LetoolRedisProperties.Cache cache = redisProperties.getCache();
        RedisCachePolicy.Builder<T> builder = RedisCachePolicy.<T>builder(ttl)
                .ttlJitter(cache.getTtlJitter())
                .lockWait(cache.getLockWait());
        return cache.isCacheNull()
                ? builder.cacheNull(cache.getNullTtl()).build()
                : builder.doNotCacheNull().build();
    }

    private RedissonClient requireRedissonClient() {
        if (redissonClient == null) {
            throw new IllegalStateException("RedissonClient 未配置，无法获取分布式锁");
        }
        return redissonClient;
    }

    private LockTemplate requireLockTemplate() {
        if (lockTemplate == null) {
            throw new IllegalStateException("分布式锁后端未配置，无法执行锁保护操作");
        }
        return lockTemplate;
    }

    private RedisCacheTemplate requireCacheTemplate() {
        if (cacheTemplate == null) {
            throw new IllegalStateException("分布式锁后端未配置，无法执行缓存回源保护");
        }
        return cacheTemplate;
    }
}
