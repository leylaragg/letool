package com.github.leyland.letool.tool.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * RedisUtil should expose native RedisTemplate operation views so callers can
     * choose the Redis data structure explicitly.
     */
    @Test
    void shouldExposeNativeRedisOperations() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        BoundValueOperations<String, Object> boundValueOperations = mock(BoundValueOperations.class);
        ListOperations<String, Object> listOperations = mock(ListOperations.class);
        BoundListOperations<String, Object> boundListOperations = mock(BoundListOperations.class);
        SetOperations<String, Object> setOperations = mock(SetOperations.class);
        BoundSetOperations<String, Object> boundSetOperations = mock(BoundSetOperations.class);
        ZSetOperations<String, Object> zSetOperations = mock(ZSetOperations.class);
        BoundZSetOperations<String, Object> boundZSetOperations = mock(BoundZSetOperations.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        BoundHashOperations<String, Object, Object> boundHashOperations = mock(BoundHashOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.boundValueOps("k")).thenReturn(boundValueOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.boundListOps("k")).thenReturn(boundListOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.boundSetOps("k")).thenReturn(boundSetOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.boundZSetOps("k")).thenReturn(boundZSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.boundHashOps("k")).thenReturn(boundHashOperations);

        RedisUtil redisUtil = new RedisUtil(redisTemplate);

        assertThat(redisUtil.opsForValue()).isSameAs(valueOperations);
        assertThat(redisUtil.boundValueOps("k")).isSameAs(boundValueOperations);
        assertThat(redisUtil.opsForList()).isSameAs(listOperations);
        assertThat(redisUtil.boundListOps("k")).isSameAs(boundListOperations);
        assertThat(redisUtil.opsForSet()).isSameAs(setOperations);
        assertThat(redisUtil.boundSetOps("k")).isSameAs(boundSetOperations);
        assertThat(redisUtil.opsForZSet()).isSameAs(zSetOperations);
        assertThat(redisUtil.boundZSetOps("k")).isSameAs(boundZSetOperations);
        assertThat(redisUtil.opsForHash()).isSameAs(hashOperations);
        assertThat(redisUtil.boundHashOps("k")).isSameAs(boundHashOperations);
    }

    /**
     * List helpers should store each element through Redis List operations instead
     * of converting objects to String values.
     */
    @Test
    void shouldUseListOperationsWithObjectElements() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ListOperations<String, Object> listOperations = mock(ListOperations.class);
        TestUser user = new TestUser("u1", "Leyland");
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush("users", user)).thenReturn(1L);
        when(listOperations.leftPop("users")).thenReturn(user);
        when(listOperations.range("users", 0, -1)).thenReturn(List.of(user));

        RedisUtil redisUtil = new RedisUtil(redisTemplate);

        assertThat(redisUtil.rpush("users", user)).isEqualTo(1L);
        TestUser popped = redisUtil.lpop("users");
        List<TestUser> users = redisUtil.lrange("users", 0, -1);

        assertThat(popped).isSameAs(user);
        assertThat(users).containsExactly(user);
    }

    /**
     * Hash, Set and ZSet helpers should also preserve RedisTemplate-deserialized
     * objects instead of stringifying them.
     */
    @Test
    void shouldUseStructuredOperationsWithObjectValues() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        SetOperations<String, Object> setOperations = mock(SetOperations.class);
        ZSetOperations<String, Object> zSetOperations = mock(ZSetOperations.class);
        TestUser user = new TestUser("u1", "Leyland");
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(hashOperations.get("users", "u1")).thenReturn(user);
        when(hashOperations.entries("users")).thenReturn(Map.of("u1", user));
        when(setOperations.add("online-users", user)).thenReturn(1L);
        when(setOperations.members("online-users")).thenReturn(Set.of(user));
        when(setOperations.isMember("online-users", user)).thenReturn(true);
        when(zSetOperations.add("rank", user, 10.0)).thenReturn(true);
        when(zSetOperations.range("rank", 0, -1)).thenReturn(Set.of(user));

        RedisUtil redisUtil = new RedisUtil(redisTemplate);

        redisUtil.hset("users", "u1", user);
        TestUser hashUser = redisUtil.hget("users", "u1");
        Map<String, TestUser> allUsers = redisUtil.hgetAll("users");
        assertThat(redisUtil.sadd("online-users", user)).isEqualTo(1L);
        Set<TestUser> members = redisUtil.smembers("online-users");
        assertThat(redisUtil.sismember("online-users", user)).isTrue();
        assertThat(redisUtil.zadd("rank", user, 10.0)).isTrue();
        Set<TestUser> ranking = redisUtil.zrange("rank", 0, -1);

        verify(hashOperations).put("users", "u1", user);
        assertThat(hashUser).isSameAs(user);
        assertThat(allUsers).containsEntry("u1", user);
        assertThat(members).containsExactly(user);
        assertThat(ranking).containsExactly(user);
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
