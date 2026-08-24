package io.github.leylaragg.letool.cache.serializer;

import io.github.leylaragg.letool.cache.exception.CacheException;

import java.lang.reflect.Type;

/**
 * 缓存序列化接口 —— 将对象序列化为字符串存入 Redis，反序列化时还原.
 */
public interface CacheSerializer {

    /**
     * 序列化对象为字符串.
     *
     * @param value 待序列化对象
     * @param <T>   对象类型
     * @return JSON 字符串
     */
    <T> String serialize(T value);

    /**
     * 反序列化字符串为对象.
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   对象类型
     * @return 反序列化后的对象
     */
    <T> T deserialize(String json, Class<T> clazz);

    /**
     * 按类或参数化类型反序列化缓存值。
     *
     * <p>旧实现无需修改即可继续处理 {@link Class}；需要支持 {@code List<DTO>}
     * 等泛型值时覆盖本方法。</p>
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @return 反序列化结果
     */
    default Object deserialize(String json, Type type) {
        if (type instanceof Class<?> clazz) {
            return deserialize(json, clazz);
        }
        throw CacheException.genericTypeUnsupported(type);
    }
}
