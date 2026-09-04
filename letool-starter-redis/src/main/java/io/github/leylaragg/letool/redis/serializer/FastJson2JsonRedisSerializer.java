package io.github.leylaragg.letool.redis.serializer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 基于 Fastjson2 的 Redis 值序列化器。
 *
 * <p>序列化结果包含类元数据，使 {@code RedisTemplate<String, Object>} 读取数据时
 * 能够恢复具体对象类型。</p>
 *
 * <p>Fastjson2 自动类型由包边界白名单保护。需要让默认 RedisTemplate 反序列化
 * 业务类型时，应用应传入自己的业务包名。Letool 会自动补充包分隔符，避免
 * {@code com.example} 意外放行 {@code com.exampleevil}。</p>
 *
 * <p>该序列化器有意不委托给通用 JSON 编解码器。Redis 多态值需要
 * {@code WriteClassName} 和独立的自动类型白名单；将这些配置应用到普通 JSON API
 * 会扩大反序列化攻击面。</p>
 *
 * @param <T> 目标值类型
 */
public class FastJson2JsonRedisSerializer<T> implements RedisSerializer<T> {

    /** Redis JSON 值使用的字符集。 */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private static final String[] DEFAULT_AUTO_TYPE_ACCEPT_PREFIXES = {
            "io.github.leylaragg"
    };

    private final Class<T> clazz;
    private final JSONReader.AutoTypeBeforeHandler autoTypeFilter;

    /**
     * 使用 Letool 内置自动类型包白名单创建序列化器。
     *
     * @param clazz 声明的 Redis 值类型，不允许为 {@code null}
     * @throws IllegalArgumentException {@code clazz} 为 {@code null} 时抛出
     */
    public FastJson2JsonRedisSerializer(Class<T> clazz) {
        this(clazz, DEFAULT_AUTO_TYPE_ACCEPT_PREFIXES);
    }

    /**
     * 使用显式指定的自动类型包白名单创建序列化器。
     *
     * <p>空白配置项会被忽略。当没有有效配置项时，将仅使用内置的 Letool 包。
     * 存储自定义多态类型或已有 Spring 类型缓存的应用应显式配置最小包范围。
     * 所有配置项传递给 Fastjson2 之前都会规范化为包边界。</p>
     *
     * @param clazz 声明的 Redis 值类型，不允许为 {@code null}
     * @param autoTypeAcceptPrefixes 允许的包名，允许为 {@code null}
     * @throws IllegalArgumentException {@code clazz} 为 {@code null} 时抛出
     */
    public FastJson2JsonRedisSerializer(Class<T> clazz, String... autoTypeAcceptPrefixes) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz must not be null");
        }
        this.clazz = clazz;
        String[] configuredPrefixes = normalizePackagePrefixes(autoTypeAcceptPrefixes);
        String[] prefixes = configuredPrefixes.length == 0
                ? normalizePackagePrefixes(DEFAULT_AUTO_TYPE_ACCEPT_PREFIXES)
                : configuredPrefixes;
        this.autoTypeFilter = JSONReader.autoTypeFilter(prefixes);
    }

    /**
     * 将配置的包名规范化为 Fastjson2 类名前缀。
     *
     * <p>末尾包分隔符属于安全边界。若直接按字符串前缀匹配，
     * {@code com.example} 也会错误匹配 {@code com.exampleevil.SomeType}。</p>
     *
     * @param packagePrefixes 配置的包名，允许为 {@code null}
     * @return 去重后的非空类名前缀，每项均以包分隔符结尾
     */
    private static String[] normalizePackagePrefixes(String[] packagePrefixes) {
        return packagePrefixes == null
                ? new String[0]
                : Arrays.stream(packagePrefixes)
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(String::trim)
                .map(prefix -> prefix.endsWith(".") ? prefix : prefix + ".")
                .distinct()
                .toArray(String[]::new);
    }

    /**
     * 将 Redis 值连同具体类元数据一起序列化。
     *
     * @param value 待序列化值，允许为 {@code null}
     * @return JSON 字节数组；值为 {@code null} 时返回空数组
     * @throws SerializationException Fastjson2 无法序列化该值时抛出
     */
    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        try {
            return JSON.toJSONBytes(
                    value,
                    DEFAULT_CHARSET,
                    JSONWriter.Feature.WriteClassName
            );
        } catch (RuntimeException exception) {
            throw new SerializationException(
                    "Could not serialize Redis value with Fastjson2",
                    exception
            );
        }
    }

    /**
     * 应用自动类型白名单后反序列化 Redis 值。
     *
     * @param bytes JSON 字节数组；为 {@code null} 或空数组时返回 {@code null}
     * @return 反序列化结果；输入为空时返回 {@code null}
     * @throws SerializationException Fastjson2 无法反序列化该值时抛出
     */
    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return JSON.parseObject(
                    bytes,
                    clazz,
                    autoTypeFilter,
                    JSONReader.Feature.ErrorOnNotSupportAutoType
            );
        } catch (RuntimeException exception) {
            // 异常消息中不得包含 Redis 原始内容，因为缓存值可能含有令牌、
            // 个人信息或其他敏感数据。
            throw new SerializationException(
                    "Could not deserialize Redis value with Fastjson2",
                    exception
            );
        }
    }
}
