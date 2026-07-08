package com.github.leyland.letool.tool.redis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FastJson2JsonRedisSerializer}.
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
                "com.github.leyland.letool.tool.redis"
        );
        TestUser user = new TestUser();
        user.setId("u2");
        user.setName("Configured");

        Object actual = serializer.deserialize(serializer.serialize(user));

        assertThat(actual).isInstanceOf(TestUser.class);
        assertThat(((TestUser) actual).getName()).isEqualTo("Configured");
    }

    @Test
    void shouldTreatNullAndEmptyAsNull() {
        FastJson2JsonRedisSerializer<Object> serializer = new FastJson2JsonRedisSerializer<>(Object.class);

        assertThat(serializer.serialize(null)).isEmpty();
        assertThat(serializer.deserialize(null)).isNull();
        assertThat(serializer.deserialize(new byte[0])).isNull();
    }

    public static class TestUser {
        private String id;
        private String name;

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
    }
}
