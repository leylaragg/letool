package com.github.leyland.letool.tool.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisUtil}.
 */
class RedisUtilTest {

    /**
     * RedisUtil should expose the application RedisTemplate so callers can rely on
     * the application's configured serializers.
     */
    @Test
    void shouldWrapObjectRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

        RedisUtil redisUtil = new RedisUtil(redisTemplate);

        assertThat(redisUtil.getTemplate()).isSameAs(redisTemplate);
    }

    /**
     * String-value operations should use RedisTemplate directly instead of forcing
     * JSON conversion inside RedisUtil.
     */
    @Test
    void shouldSetAndGetSerializedObjectsThroughRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        TestUser user = new TestUser("u1", "Leyland");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:1")).thenReturn(user);

        RedisUtil redisUtil = new RedisUtil(redisTemplate);

        redisUtil.set("user:1", user, Duration.ofMinutes(5));
        TestUser actual = redisUtil.get("user:1", TestUser.class);

        verify(valueOperations).set("user:1", user, Duration.ofMinutes(5));
        assertThat(actual).isSameAs(user);
    }

    /**
     * Generic get should return the deserialized object produced by RedisTemplate
     * without converting it to String.
     */
    @Test
    void shouldReturnTemplateDeserializedObjectFromGenericGet() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        TestUser user = new TestUser("u1", "Leyland");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:1")).thenReturn(user);

        RedisUtil redisUtil = new RedisUtil(redisTemplate);

        TestUser actual = redisUtil.get("user:1");

        assertThat(actual).isSameAs(user);
    }

    /**
     * Typed get should return the serializer-produced object when it already
     * matches the requested type.
     */
    @Test
    void shouldGetObjectByRequestedType() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        TestUser user = new TestUser("u1", "Leyland");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:1")).thenReturn(user);

        RedisUtil redisUtil = new RedisUtil(redisTemplate);

        TestUser actual = redisUtil.get("user:1", TestUser.class);

        assertThat(actual).isSameAs(user);
    }

    /**
     * Pipeline execution should pass Redis operations to the caller callback.
     */
    @Test
    void pipelineShouldExecuteConsumerWithRedisOperations() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        List<Object> expected = List.of("ok");
        when(redisTemplate.executePipelined(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            callback.execute(redisTemplate);
            return expected;
        });

        RedisUtil redisUtil = new RedisUtil(redisTemplate);
        AtomicReference<RedisOperations<String, Object>> operationsRef = new AtomicReference<>();

        List<Object> actual = redisUtil.pipeline(operationsRef::set);

        assertThat(actual).isSameAs(expected);
        assertThat(operationsRef.get()).isSameAs(redisTemplate);
    }

    record TestUser(String id, String name) {
    }
}
