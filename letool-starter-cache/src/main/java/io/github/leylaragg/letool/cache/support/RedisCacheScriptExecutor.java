package io.github.leylaragg.letool.cache.support;

import io.github.leylaragg.letool.redis.RedisFacade;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;
import java.util.Objects;

/**
 * 缓存模块内部的 Redis Lua 执行器。
 *
 * <p>该执行器只依赖 {@link RedisFacade#getTemplate()} 这一稳定接口，由 Cache 自己声明脚本返回类型。
 * 因此 Cache 单模块编译不要求本地仓库预先安装带新重载的 Tool，同时也能正确处理 Lettuce 整数
 * 输出和预序列化业务值的原始字节。</p>
 */
public final class RedisCacheScriptExecutor {

    /** 业务值原样透传，其它 Lua 参数按 UTF-8 字符串编码。 */
    private static final RedisSerializer<Object> ARGUMENT_SERIALIZER = new RedisSerializer<>() {
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

    private RedisCacheScriptExecutor() {
    }

    /**
     * 使用纯字符串元数据参数执行 Lua，并按调用方声明的类型解析结果。
     *
     * @param redisFacade Redis 操作入口
     * @param script Lua 脚本内容
     * @param resultType 脚本返回类型
     * @param keys Redis Key 列表
     * @param args Lua ARGV；{@code byte[]} 保持原始字节，其它值转成字符串
     * @param <T> 脚本返回值类型
     * @return 脚本执行结果
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T executeRaw(
            RedisFacade redisFacade,
            String script,
            Class<T> resultType,
            List<String> keys,
            Object... args) {
        RedisTemplate<String, Object> template = Objects.requireNonNull(
                redisFacade, "Redis 操作入口不能为空").getTemplate();
        DefaultRedisScript<T> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(Objects.requireNonNull(script, "Lua 脚本不能为空"));
        redisScript.setResultType(Objects.requireNonNull(resultType, "脚本返回类型不能为空"));
        RedisSerializer resultSerializer = RedisSerializer.string();
        return (T) template.execute(
                redisScript, ARGUMENT_SERIALIZER, resultSerializer,
                Objects.requireNonNull(keys, "Redis Key 列表不能为空"), args);
    }
}
