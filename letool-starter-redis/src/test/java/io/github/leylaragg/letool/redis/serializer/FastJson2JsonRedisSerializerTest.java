package io.github.leylaragg.letool.redis.serializer;

import io.github.leylaragg.letool.redis.serializer.allowed.AllowedValue;
import io.github.leylaragg.letool.redis.serializer.allowedevil.LookalikeValue;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.SerializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FastJson2JsonRedisSerializer} 单元测试。
 */
class FastJson2JsonRedisSerializerTest {

    @Test
    void shouldRoundTripObjectWithTypeMetadata() {
        FastJson2JsonRedisSerializer<Object> serializer = new FastJson2JsonRedisSerializer<>(Object.class);
        TestUser user = new TestUser();
        user.setId("u1");
        user.setName("Leyland");

        byte[] bytes = serializer.serialize(user);
        Object actual = serializer.deserialize(bytes);

        assertThat(actual).isInstanceOf(TestUser.class);
        assertThat(((TestUser) actual).getId()).isEqualTo("u1");
        assertThat(((TestUser) actual).getName()).isEqualTo("Leyland");
    }

    @Test
    void shouldUseConfiguredAutoTypeAcceptPrefixes() {
        FastJson2JsonRedisSerializer<Object> serializer = new FastJson2JsonRedisSerializer<>(
                Object.class,
                "io.github.leylaragg.letool.redis.serializer"
        );
        TestUser user = new TestUser();
        user.setId("u2");
        user.setName("Configured");

        Object actual = serializer.deserialize(serializer.serialize(user));

        assertThat(actual).isInstanceOf(TestUser.class);
        assertThat(((TestUser) actual).getName()).isEqualTo("Configured");
    }

    @Test
    void shouldAcceptOnlyClassesInsideConfiguredPackageBoundary() {
        FastJson2JsonRedisSerializer<Object> serializer = new FastJson2JsonRedisSerializer<>(
                Object.class,
                "io.github.leylaragg.letool.redis.serializer.allowed"
        );
        AllowedValue value = new AllowedValue("allowed");

        Object actual = serializer.deserialize(serializer.serialize(value));

        assertThat(actual).isInstanceOf(AllowedValue.class);
        assertThat(((AllowedValue) actual).getValue()).isEqualTo("allowed");
    }

    @Test
    void shouldRejectLookalikePackageOutsideConfiguredBoundary() {
        FastJson2JsonRedisSerializer<Object> serializer = new FastJson2JsonRedisSerializer<>(
                Object.class,
                "io.github.leylaragg.letool.redis.serializer.allowed"
        );
        LookalikeValue value = new LookalikeValue("must-not-load");
        byte[] bytes = serializer.serialize(value);

        assertThatThrownBy(() -> serializer.deserialize(bytes))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("deserialize");
    }

    @Test
    void shouldTreatNullAndEmptyAsNull() {
        FastJson2JsonRedisSerializer<Object> serializer = new FastJson2JsonRedisSerializer<>(Object.class);

        assertThat(serializer.serialize(null)).isEmpty();
        assertThat(serializer.deserialize(null)).isNull();
        assertThat(serializer.deserialize(new byte[0])).isNull();
    }

    @Test
    void shouldRejectMissingTargetType() {
        assertThatThrownBy(() -> new FastJson2JsonRedisSerializer<>(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clazz");
    }

    @Test
    void shouldWrapSerializationFailure() {
        FastJson2JsonRedisSerializer<Object> serializer = new FastJson2JsonRedisSerializer<>(Object.class);

        assertThatThrownBy(() -> serializer.serialize(new BrokenValue()))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("serialize")
                .hasRootCauseMessage("broken getter");
    }

    @Test
    void shouldWrapDeserializationFailureWithoutExposingPayload() {
        FastJson2JsonRedisSerializer<TestUser> serializer =
                new FastJson2JsonRedisSerializer<>(TestUser.class);
        byte[] invalidPayload = "{\"count\":\"sensitive-invalid-number\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(invalidPayload))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("deserialize")
                .hasMessageNotContaining("sensitive-invalid-number");
    }

    public static class TestUser {
        private String id;
        private String name;
        private int count;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public static class BrokenValue {

        public String getValue() {
            throw new IllegalStateException("broken getter");
        }
    }
}
