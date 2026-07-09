package com.github.leyland.letool.tool.redis;

import com.github.leyland.letool.tool.util.JsonUtil;
import org.jetbrains.annotations.NotNull;
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
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类，底层基于 {@link RedisTemplate}。
 *
 * <h3>设计说明</h3>
 * <p>本工具类不内置 JSON 序列化器，也不强制创建默认 {@code RedisTemplate}。
 * 应用侧通常会配置自己的 key/value/hash 序列化方案，例如 Fastjson2、Jackson 或 JDK 序列化。
 * {@code RedisUtil} 只负责把值交给 {@code RedisTemplate}，从而复用应用已有的序列化配置。</p>
 *
 * <h3>自动装配</h3>
 * <p>starter 只在应用上下文中存在名为 {@code redisTemplate} 的对象模板时创建本工具类。
 * 只有 {@code StringRedisTemplate} 时不会自动创建，避免误以为对象序列化可用。</p>
 *
 * <h3>支持的操作类型</h3>
 * <ul>
 *   <li><b>Key</b>：存在判断、删除、过期时间设置与查询</li>
 *   <li><b>Value</b>：set/get/increment，支持应用 RedisTemplate 序列化后的任意对象</li>
 *   <li><b>Hash</b>：hset/hget/hgetAll/hdel</li>
 *   <li><b>List</b>：lpush/rpush/lpop/rpop/lrange</li>
 *   <li><b>Set</b>：sadd/smembers/sismember</li>
 *   <li><b>ZSet</b>：zadd/zrange</li>
 *   <li><b>Lua 脚本</b>：executeScript</li>
 *   <li><b>管道</b>：pipeline 批量操作</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 字符串：RedisTemplate 反序列化后按调用方声明类型返回
 * redisUtil.set("user:name", "张三", Duration.ofHours(1));
 * String name = redisUtil.get("user:name");
 *
 * // 对象：由应用配置的 RedisTemplate 序列化器负责序列化和反序列化
 * redisUtil.set("user:1", user, Duration.ofHours(1));
 * User cachedUser = redisUtil.get("user:1", User.class);
 *
 * // 兼容旧对象方法名
 * redisUtil.setObject("user:2", user, Duration.ofHours(1));
 * User user2 = redisUtil.getObject("user:2", User.class);
 *
 * // Hash
 * redisUtil.hset("user:1", "name", "张三");
 * Map<String, String> all = redisUtil.hgetAll("user:1");
 *
 * // Lua 脚本
 * String script = "return redis.call('GET', KEYS[1])";
 * String result = redisUtil.executeScript(script, List.of("key1"));
 * }</pre>
 */
public class RedisUtil {

    /** 应用侧配置好的 RedisTemplate，value/hashValue 序列化方案由应用决定。 */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis 工具类。
     *
     * @param redisTemplate 应用侧对象 RedisTemplate
     */
    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取底层 RedisTemplate，用于调用工具类未封装的原生 Redis 操作。
     *
     * @return RedisTemplate 实例
     */
    public RedisTemplate<String, Object> getTemplate() {
        return redisTemplate;
    }

    /**
     * 获取 Redis Value 原生操作视图。
     *
     * <p>读写值都会直接使用应用配置的 RedisTemplate value serializer，不做二次 JSON 处理或字符串转换。</p>
     */
    public ValueOperations<String, Object> opsForValue() {
        return redisTemplate.opsForValue();
    }

    /**
     * 获取绑定到指定 key 的 Redis Value 原生操作视图。
     *
     * <p>适合缓存层在已拼好 Redis key 后直接调用 get/set/increment 等操作。</p>
     */
    public BoundValueOperations<String, Object> boundValueOps(String key) {
        return redisTemplate.boundValueOps(key);
    }

    /**
     * 获取 Redis List 原生操作视图。
     *
     * <p>List 元素会逐条使用 RedisTemplate value serializer 序列化，适合真实 Redis List 结构。</p>
     */
    public ListOperations<String, Object> opsForList() {
        return redisTemplate.opsForList();
    }

    /**
     * 获取绑定到指定 key 的 Redis List 原生操作视图。
     */
    public BoundListOperations<String, Object> boundListOps(String key) {
        return redisTemplate.boundListOps(key);
    }

    /**
     * 获取 Redis Set 原生操作视图。
     *
     * <p>Set 成员保持对象形态交给 RedisTemplate serializer 处理，不会先转成 String。</p>
     */
    public SetOperations<String, Object> opsForSet() {
        return redisTemplate.opsForSet();
    }

    /**
     * 获取绑定到指定 key 的 Redis Set 原生操作视图。
     */
    public BoundSetOperations<String, Object> boundSetOps(String key) {
        return redisTemplate.boundSetOps(key);
    }

    /**
     * 获取 Redis ZSet 原生操作视图。
     *
     * <p>member 使用 RedisTemplate serializer，score 使用 Redis 原生 double 分数。</p>
     */
    public ZSetOperations<String, Object> opsForZSet() {
        return redisTemplate.opsForZSet();
    }

    /**
     * 获取绑定到指定 key 的 Redis ZSet 原生操作视图。
     */
    public BoundZSetOperations<String, Object> boundZSetOps(String key) {
        return redisTemplate.boundZSetOps(key);
    }

    /**
     * 获取 Redis Hash 原生操作视图。
     *
     * <p>Hash field/value 分别使用 RedisTemplate 的 hashKey/hashValue serializer。</p>
     */
    public HashOperations<String, Object, Object> opsForHash() {
        return redisTemplate.opsForHash();
    }

    /**
     * 获取绑定到指定 key 的 Redis Hash 原生操作视图。
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
     * <p>旧版本方法名保留为 {@code setObject}，但不再在工具类内部强制 JSON 序列化，
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
        DefaultRedisScript redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Object.class);
        // 将所有 args 转为纯字符串，绕过 value serializer 避免 JSON 包装
        String[] rawArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof byte[] bytes) {
                rawArgs[i] = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                rawArgs[i] = args[i].toString();
            }
        }
        RedisSerializer stringSerializer = RedisSerializer.string();
        return (T) redisTemplate.execute(
                redisScript,
                stringSerializer,
                stringSerializer,
                keys,
                (Object[]) rawArgs);
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
            public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) {
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
}
