package io.github.leylaragg.letool.cache.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 缓存私有 Redis 视图的 Value 序列化适配器。
 *
 * <p>业务数据仍使用应用模板的序列化协议；Lua 和 Redis 计数命令产生的版本元数据是纯数字
 * 字节，业务反序列化器无法识别时才按 UTF-8 数字读取。回退范围限定为十进制整数，避免把
 * 损坏的业务缓存值静默转换成字符串。</p>
 */
final class CacheRedisValueSerializer implements RedisSerializer<Object> {

    private final RedisSerializer<Object> delegate;

    /**
     * @param delegate 业务 RedisTemplate 的 Value 序列化器
     */
    @SuppressWarnings("unchecked")
    CacheRedisValueSerializer(RedisSerializer<?> delegate) {
        this.delegate = (RedisSerializer<Object>) Objects.requireNonNull(
                delegate, "业务 Value 序列化器不能为空");
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        return delegate.serialize(value);
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        try {
            return delegate.deserialize(bytes);
        } catch (SerializationException exception) {
            if (isDecimalMetadata(bytes)) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            throw exception;
        }
    }

    /** 只接纳 Redis 版本计数器可能产生的十进制整数。 */
    private boolean isDecimalMetadata(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int start = bytes[0] == '-' ? 1 : 0;
        if (start == bytes.length) {
            return false;
        }
        for (int index = start; index < bytes.length; index++) {
            if (bytes[index] < '0' || bytes[index] > '9') {
                return false;
            }
        }
        return true;
    }
}
