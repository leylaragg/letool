package com.github.leyland.letool.tool.redis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.Filter;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Fastjson2 Redis value serializer.
 *
 * <p>Serializes values as JSON with class metadata so a {@code RedisTemplate<String, Object>}
 * can restore concrete object types when reading.</p>
 *
 * <p>Fastjson2 auto type is guarded by an accept-prefix filter. Applications should
 * pass their own package prefixes when they want the default RedisTemplate to
 * deserialize application classes.</p>
 *
 * @param <T> target value type
 */
public class FastJson2JsonRedisSerializer<T> implements RedisSerializer<T> {

    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private static final String[] DEFAULT_AUTO_TYPE_ACCEPT_PREFIXES = {
            "org.springframework",
            "com.github.leyland"
    };

    private final Class<T> clazz;
    private final Filter autoTypeFilter;

    public FastJson2JsonRedisSerializer(Class<T> clazz) {
        this(clazz, DEFAULT_AUTO_TYPE_ACCEPT_PREFIXES);
    }

    public FastJson2JsonRedisSerializer(Class<T> clazz, String... autoTypeAcceptPrefixes) {
        this.clazz = clazz;
        String[] configuredPrefixes = autoTypeAcceptPrefixes == null
                ? new String[0]
                : Arrays.stream(autoTypeAcceptPrefixes)
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .toArray(String[]::new);
        String[] prefixes = configuredPrefixes.length == 0
                ? DEFAULT_AUTO_TYPE_ACCEPT_PREFIXES
                : configuredPrefixes;
        this.autoTypeFilter = JSONReader.autoTypeFilter(prefixes);
    }

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        return JSON.toJSONString(value, JSONWriter.Feature.WriteClassName).getBytes(DEFAULT_CHARSET);
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String text = new String(bytes, DEFAULT_CHARSET);
        return JSON.parseObject(text, clazz, autoTypeFilter);
    }
}
