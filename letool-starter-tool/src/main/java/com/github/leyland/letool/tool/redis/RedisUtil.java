package com.github.leyland.letool.tool.redis;

import com.github.leyland.letool.tool.util.CollUtil;
import com.github.leyland.letool.tool.util.JsonUtil;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis operation helper backed by {@link StringRedisTemplate}.
 *
 * <h3>Activation</h3>
 * <p>The tool starter registers this helper only when a {@link StringRedisTemplate}
 * bean exists. Applications can also instantiate it directly for plain utility usage.</p>
 *
 * <h3>支持的操作类型</h3>
 * <ul>
 *   <li><b>Key</b>：存在判断、删除、过期设置</li>
 *   <li><b>String</b>：get/set/increment + 对象 JSON 序列化存取</li>
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
 * // 字符串
 * redisUtil.set("user:1", "张三", Duration.ofHours(1));
 * String name = redisUtil.get("user:1");
 *
 * // 对象存取（JSON 序列化）
 * redisUtil.setObject("user:1", user, Duration.ofHours(1));
 * User user = redisUtil.getObject("user:1", User.class);
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

    private final StringRedisTemplate redisTemplate;

    public RedisUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取底层 StringRedisTemplate，用于自定义操作.
     *
     * @return StringRedisTemplate 实例
     */
    public StringRedisTemplate getTemplate() {
        return redisTemplate;
    }

    // ======================== Key 操作 ========================

    /**
     * 判断 key 是否存在.
     *
     * @param key 键
     * @return {@code true} 如果存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 删除 key.
     *
     * @param key 键
     * @return {@code true} 如果成功删除
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 批量删除 key.
     *
     * @param keys 键集合
     * @return 实际删除的 key 数量
     */
    public long delete(Collection<String> keys) {
        Long count = redisTemplate.delete(keys);
        return count == null ? 0 : count;
    }

    /**
     * 设置 key 的过期时间.
     *
     * @param key     键
     * @param timeout 时长
     * @param unit    时间单位
     * @return {@code true} 如果设置成功
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /**
     * 获取 key 的剩余有效时间.
     *
     * @param key  键
     * @param unit 时间单位
     * @return 剩余秒数（毫秒等），key 不存在返回 -1
     */
    public long getExpire(String key, TimeUnit unit) {
        Long ttl = redisTemplate.getExpire(key, unit);
        return ttl == null ? -1 : ttl;
    }

    // ======================== String 操作 ========================

    /** 设置字符串值（永不过期） */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置字符串值（带过期时间）.
     *
     * @param key      键
     * @param value    值
     * @param duration 过期时长
     */
    public void set(String key, String value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    /**
     * 设置字符串值（带过期时间）.
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 获取字符串值.
     *
     * @param key 键
     * @return 值，key 不存在返回 {@code null}
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 自增（或自减）.
     *
     * @param key   键
     * @param delta 增量（可为负数）
     * @return 自增后的值
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    // ======================== 对象存取（JSON 序列化） ========================

    /**
     * 将对象以 JSON 格式存入 Redis.
     *
     * @param key      键
     * @param obj      任意 Java 对象
     * @param duration 过期时长
     * @param <T>      对象类型
     */
    public <T> void setObject(String key, T obj, Duration duration) {
        redisTemplate.opsForValue().set(key, JsonUtil.toJsonString(obj), duration);
    }

    /**
     * 从 Redis 读取 JSON 并反序列化为对象.
     *
     * @param key   键
     * @param clazz 目标类型
     * @param <T>   对象类型
     * @return 反序列化后的对象，key 不存在返回 {@code null}
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = get(key);
        return JsonUtil.parseObject(json, clazz);
    }

    // ======================== Hash 操作 ========================

    /** 设置 Hash 字段 */
    public void hset(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * 获取 Hash 字段值.
     *
     * @param key   键
     * @param field 字段名
     * @return 字段值，不存在返回 {@code null}
     */
    public String hget(String key, String field) {
        Object val = redisTemplate.opsForHash().get(key, field);
        return val == null ? null : val.toString();
    }

    /**
     * 获取 Hash 中所有字段和值.
     *
     * @param key 键
     * @return field → value 的 LinkedHashMap
     */
    public Map<String, String> hgetAll(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<String, String> result = new LinkedHashMap<>();
        entries.forEach((k, v) -> result.put(k.toString(), v == null ? null : v.toString()));
        return result;
    }

    /**
     * 删除 Hash 中的指定字段.
     *
     * @param key    键
     * @param fields 字段名列表
     * @return 实际删除的字段数
     */
    public long hdel(String key, String... fields) {
        Long count = redisTemplate.opsForHash().delete(key, (Object[]) fields);
        return count == null ? 0 : count;
    }

    // ======================== List 操作 ========================

    /** 从左侧推入（返回列表长度） */
    public long lpush(String key, String value) {
        Long size = redisTemplate.opsForList().leftPush(key, value);
        return size == null ? 0 : size;
    }

    /** 从右侧推入（返回列表长度） */
    public long rpush(String key, String value) {
        Long size = redisTemplate.opsForList().rightPush(key, value);
        return size == null ? 0 : size;
    }

    /** 从左侧弹出 */
    public String lpop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /** 从右侧弹出 */
    public String rpop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 获取列表指定范围的元素.
     *
     * @param key   键
     * @param start 起始索引（包含，0 为第一个）
     * @param end   结束索引（包含，-1 表示最后一个）
     * @return 元素列表
     */
    public List<String> lrange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    // ======================== Set 操作 ========================

    /** 添加元素到 Set（返回成功添加的数量） */
    public long sadd(String key, String... values) {
        Long count = redisTemplate.opsForSet().add(key, values);
        return count == null ? 0 : count;
    }

    /** 获取 Set 所有成员 */
    public Set<String> smembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /** 判断元素是否在 Set 中 */
    public boolean sismember(String key, String value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    // ======================== ZSet 操作 ========================

    /**
     * 添加元素到有序集合.
     *
     * @param key   键
     * @param value 值
     * @param score 分数
     * @return {@code true} 如果成功添加
     */
    public boolean zadd(String key, String value, double score) {
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
    }

    /**
     * 获取有序集合指定排名范围的元素（按分数升序）.
     *
     * @param key   键
     * @param start 起始排名
     * @param end   结束排名（-1 表示最后一个）
     * @return 元素集合
     */
    public Set<String> zrange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    // ======================== Lua 脚本 ========================

    /**
     * 执行 Lua 脚本.
     *
     * <p>脚本中通过 {@code KEYS[1]} 访问 keys 参数，通过 {@code ARGV[1]} 访问 args 参数.</p>
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

    // ======================== Pipeline ========================

    /**
     * 执行管道批量操作——减少网络往返，适合批量写入场景.
     *
     * @param consumer 管道操作回调（接收 RedisOperations 执行批量命令）
     * @return 每条命令的返回值列表
     */
    public List<Object> pipeline(java.util.function.Consumer<RedisOperations<String, String>> consumer) {
        return redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> stringOperations = (RedisOperations<String, String>) operations;
                consumer.accept(stringOperations);
                return null;
            }
        });
    }
}
