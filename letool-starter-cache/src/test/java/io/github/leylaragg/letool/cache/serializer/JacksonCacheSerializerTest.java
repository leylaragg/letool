package io.github.leylaragg.letool.cache.serializer;

import io.github.leylaragg.letool.cache.exception.CacheException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 泛型缓存值序列化契约测试。 */
@DisplayName("JacksonCacheSerializer 泛型类型测试")
class JacksonCacheSerializerTest {

    @Test
    @DisplayName("参数化 List 反序列化后保留 DTO 元素类型")
    void shouldDeserializeParameterizedList() throws Exception {
        Type type = Types.class.getDeclaredField("rules").getGenericType();
        CacheSerializer serializer = new JacksonCacheSerializer();

        Object decoded = serializer.deserialize(
                "[{\"code\":\"R1\",\"enabled\":true}]", type);

        assertInstanceOf(List.class, decoded);
        Object first = ((List<?>) decoded).get(0);
        assertInstanceOf(RuleDto.class, first);
        assertEquals("R1", ((RuleDto) first).code());
    }

    @Test
    @DisplayName("旧自定义序列化器对泛型 Type 给出明确稳定错误")
    void legacySerializerShouldRejectGenericTypeExplicitly() throws Exception {
        Type type = Types.class.getDeclaredField("rules").getGenericType();
        CacheSerializer legacy = new CacheSerializer() {
            @Override
            public <T> String serialize(T value) { return String.valueOf(value); }

            @Override
            public <T> T deserialize(String json, Class<T> clazz) { return null; }
        };

        CacheException exception = assertThrows(
                CacheException.class,
                () -> legacy.deserialize("[]", type)
        );

        assertEquals("CACHE_007", exception.getCode());
    }

    private static final class Types {
        private List<RuleDto> rules;
    }

    record RuleDto(String code, boolean enabled) { }
}
