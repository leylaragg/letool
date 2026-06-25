package com.github.leyland.letool.cache.serializer;

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
}
