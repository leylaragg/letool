package io.github.leylaragg.letool.tool.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.alibaba.fastjson2.JSONWriter;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证兼容门面及其显式编解码器扩展点。
 */
class JsonUtilTest {

    static class User {
        private String name;
        private int age;

        User() {
        }

        User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    @Test
    void shouldSerializeWithCompatibilityDefault() {
        User user = new User("张三", 25);

        String json = JsonUtil.toJsonString(user);

        assertThat(json).contains("\"name\":\"张三\"");
    }

    @Test
    void shouldDeserializeObject() {
        String json = "{\"name\":\"李四\",\"age\":30}";

        User user = JsonUtil.parseObject(json, User.class);

        assertThat(user.getName()).isEqualTo("李四");
        assertThat(user.getAge()).isEqualTo(30);
    }

    @Test
    void shouldDeserializeArray() {
        String json = "[{\"name\":\"a\",\"age\":1},{\"name\":\"b\",\"age\":2}]";

        List<User> users = JsonUtil.parseArray(json, User.class);

        assertThat(users).hasSize(2);
    }

    @Test
    void shouldConvertBeanToMap() {
        User user = new User("test", 30);

        Map<String, Object> map = JsonUtil.toMap(user);

        assertThat(map)
                .containsEntry("name", "test")
                .containsEntry("age", 30);
    }

    @Test
    void shouldPreserveLegacyPrettyWriterBehavior() {
        User user = new User(null, 0);

        String actual = JsonUtil.toPrettyJson(user);
        String legacy = JSON.toJSONString(user, JSONWriter.Feature.PrettyFormat);

        assertThat(actual).isEqualTo(legacy);
    }

    @Test
    void shouldPreserveLegacyMapConversionBehavior() {
        User user = new User(null, 0);
        Map<String, Object> legacy = JSON.parseObject(JSON.toJSONString(user), Map.class);

        Map<String, Object> actual = JsonUtil.toMap(user);

        assertThat(actual).isEqualTo(legacy);
    }

    @Test
    void shouldPreserveLegacyByteSerializationBehavior() {
        User user = new User(null, 0);
        byte[] legacy = JSON.toJSONBytes(
                user,
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.NotWriteDefaultValue
        );

        byte[] actual = JsonUtil.toJsonBytes(user);

        assertThat(actual).containsExactly(legacy);
    }

    @Test
    void shouldPreserveLegacyByteDeserializationBehavior() {
        byte[] json = JSON.toJSONBytes(new User("bytes", 12));

        User actual = JsonUtil.parseObject(json, User.class);
        User legacy = JSON.parseObject(json, User.class);

        assertThat(actual.getName()).isEqualTo(legacy.getName());
        assertThat(actual.getAge()).isEqualTo(legacy.getAge());
    }

    @Test
    void shouldPreserveLegacyParameterizedTypeBehavior() {
        String json = "[{\"name\":\"generic\",\"age\":20}]";
        Type type = new TypeReference<List<User>>() {
        }.getType();

        List<User> actual = JsonUtil.parseObject(json, type);
        List<User> legacy = JSON.parseObject(json, type);

        assertThat(actual).hasSize(1);
        assertThat(actual.get(0).getName()).isEqualTo(legacy.get(0).getName());
        assertThat(actual.get(0).getAge()).isEqualTo(legacy.get(0).getAge());
    }

    @Test
    void shouldPreserveLegacyProviderNodeBehavior() {
        String objectJson = "{\"name\":\"node\",\"age\":8}";
        String arrayJson = "[" + objectJson + "]";

        JSONObject actualObject = JsonUtil.parseObject(objectJson);
        JSONArray actualArray = JsonUtil.parseArray(arrayJson);

        assertThat(actualObject).isEqualTo(JSON.parseObject(objectJson));
        assertThat(actualArray).isEqualTo(JSON.parseArray(arrayJson));
    }

    @Test
    void shouldPreserveLegacyMapToBeanBehavior() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "map");
        source.put("age", 16);

        User actual = JsonUtil.toBean(source, User.class);
        User legacy = JSON.to(User.class, source);

        assertThat(actual.getName()).isEqualTo(legacy.getName());
        assertThat(actual.getAge()).isEqualTo(legacy.getAge());
    }

    @Test
    void shouldPreserveLegacyObjectConversionBehavior() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "convert");
        source.put("age", 22);

        User actual = JsonUtil.convert(source, User.class);
        User legacy = JSON.parseObject(
                JSON.toJSONString(
                        source,
                        JSONWriter.Feature.WriteMapNullValue,
                        JSONWriter.Feature.NotWriteDefaultValue
                ),
                User.class
        );

        assertThat(actual.getName()).isEqualTo(legacy.getName());
        assertThat(actual.getAge()).isEqualTo(legacy.getAge());
    }

    @Test
    void shouldUseExplicitCodecWithoutChangingGlobalState() {
        JsonCodec codec = mock(JsonCodec.class);
        User user = new User("custom", 18);
        when(codec.write(user)).thenReturn("{\"source\":\"custom\"}");

        String actual = JsonUtil.toJsonString(user, codec);

        assertThat(actual).isEqualTo("{\"source\":\"custom\"}");
        verify(codec).write(user);
    }

    @Test
    void shouldShortCircuitNullBeforeCallingExplicitCodec() {
        JsonCodec codec = mock(JsonCodec.class);

        String actual = JsonUtil.toJsonString(null, codec);

        assertThat(actual).isNull();
        verify(codec, never()).write(any());
    }
}
