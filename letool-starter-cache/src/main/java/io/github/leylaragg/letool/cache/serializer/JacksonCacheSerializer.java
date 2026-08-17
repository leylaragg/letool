package io.github.leylaragg.letool.cache.serializer;

import io.github.leylaragg.letool.tool.util.JsonUtil;

/**
 * Jackson 缓存序列化器 —— 基于 tool 模块的 {@link JsonUtil}，默认实现.
 */
public class JacksonCacheSerializer implements CacheSerializer {

    @Override
    public <T> String serialize(T value) {
        return JsonUtil.toJsonString(value);
    }

    @Override
    public <T> T deserialize(String json, Class<T> clazz) {
        return JsonUtil.parseObject(json, clazz);
    }
}
