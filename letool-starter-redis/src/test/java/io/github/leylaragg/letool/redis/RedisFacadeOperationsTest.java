package io.github.leylaragg.letool.redis;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisFacade}.
 */
class RedisFacadeOperationsTest {

    /**
     * RedisFacade should expose the application RedisTemplate so callers can rely on
     * the application's configured serializers.
     */
    @Test
    void shouldWrapObjectRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        assertThat(redisFacade.getTemplate()).isSameAs(redisTemplate);
    }

    /**
     * String-value operations should use RedisTemplate directly instead of forcing
     * JSON conversion inside RedisFacade.
     */
    @Test
    void shouldSetAndGetSerializedObjectsThroughRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        TestUser user = new TestUser("u1", "Leyland");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:1")).thenReturn(user);

        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        redisFacade.set("user:1", user, Duration.ofMinutes(5));
        TestUser actual = redisFacade.get("user:1", TestUser.class);

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

        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        TestUser actual = redisFacade.get("user:1");

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

        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        TestUser actual = redisFacade.get("user:1", TestUser.class);

        assertThat(actual).isSameAs(user);
    }

    /**
     * RedisFacade should expose native RedisTemplate operation views so callers can
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

        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        assertThat(redisFacade.opsForValue()).isSameAs(valueOperations);
        assertThat(redisFacade.boundValueOps("k")).isSameAs(boundValueOperations);
        assertThat(redisFacade.opsForList()).isSameAs(listOperations);
        assertThat(redisFacade.boundListOps("k")).isSameAs(boundListOperations);
        assertThat(redisFacade.opsForSet()).isSameAs(setOperations);
        assertThat(redisFacade.boundSetOps("k")).isSameAs(boundSetOperations);
        assertThat(redisFacade.opsForZSet()).isSameAs(zSetOperations);
        assertThat(redisFacade.boundZSetOps("k")).isSameAs(boundZSetOperations);
        assertThat(redisFacade.opsForHash()).isSameAs(hashOperations);
        assertThat(redisFacade.boundHashOps("k")).isSameAs(boundHashOperations);
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

        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        assertThat(redisFacade.rpush("users", user)).isEqualTo(1L);
        TestUser popped = redisFacade.lpop("users");
        List<TestUser> users = redisFacade.lrange("users", 0, -1);

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

        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        redisFacade.hset("users", "u1", user);
        TestUser hashUser = redisFacade.hget("users", "u1");
        Map<String, TestUser> allUsers = redisFacade.hgetAll("users");
        assertThat(redisFacade.sadd("online-users", user)).isEqualTo(1L);
        Set<TestUser> members = redisFacade.smembers("online-users");
        assertThat(redisFacade.sismember("online-users", user)).isTrue();
        assertThat(redisFacade.zadd("rank", user, 10.0)).isTrue();
        Set<TestUser> ranking = redisFacade.zrange("rank", 0, -1);

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

        RedisFacade redisFacade = new RedisFacade(redisTemplate);
        AtomicReference<RedisOperations<String, Object>> operationsRef = new AtomicReference<>();

        List<Object> actual = redisFacade.pipeline(operationsRef::set);

        assertThat(actual).isSameAs(expected);
        assertThat(operationsRef.get()).isSameAs(redisTemplate);
    }

    /** Lua 整数结果必须声明为 Long，避免 Lettuce 误用对象 Value 输出解码。 */
    @Test
    void rawScriptShouldUseDeclaredResultType() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                anyList(),
                any(Object[].class))).thenReturn(1L);
        RedisFacade redisFacade = new RedisFacade(redisTemplate);
        byte[] serializedValue = new byte[]{(byte) 0xAC, (byte) 0xED, 0x00, 0x05};

        Long result = redisFacade.executeScriptRaw(
                "return 1", Long.class, List.of("cache:key"), serializedValue);

        ArgumentCaptor<RedisScript<?>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<RedisSerializer<?>> argumentSerializerCaptor =
                ArgumentCaptor.forClass(RedisSerializer.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                argumentSerializerCaptor.capture(),
                any(RedisSerializer.class),
                anyList(),
                argumentsCaptor.capture());
        assertThat(result).isEqualTo(1L);
        assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(Long.class);
        assertThat(argumentsCaptor.getValue()).containsExactly((Object) serializedValue);
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> argumentSerializer =
                (RedisSerializer<Object>) argumentSerializerCaptor.getValue();
        assertThat(argumentSerializer.serialize(serializedValue)).isSameAs(serializedValue);
    }

    /** Spring Data 未返回 TTL 时，门面应遵守文档约定并返回 -1。 */
    @Test
    void nullTtlShouldFollowDocumentedMinusOneContract() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.getExpire("missing", TimeUnit.SECONDS)).thenReturn(null);
        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        assertThat(redisFacade.getExpire("missing", TimeUnit.SECONDS)).isEqualTo(-1L);
    }

    /** 可空布尔结果不得通过自动拆箱泄漏为空指针异常。 */
    @Test
    void nullBooleanResultsShouldBeNormalizedToFalse() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.hasKey("missing")).thenReturn(null);
        when(redisTemplate.delete("missing")).thenReturn(null);
        when(redisTemplate.expire("missing", 30, TimeUnit.SECONDS)).thenReturn(null);
        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        assertThat(redisFacade.hasKey("missing")).isFalse();
        assertThat(redisFacade.delete("missing")).isFalse();
        assertThat(redisFacade.expire("missing", 30, TimeUnit.SECONDS)).isFalse();
    }

    /** 可空计数结果应统一收敛为零，便于调用方直接进行数值判断。 */
    @Test
    void nullCountResultsShouldBeNormalizedToZero() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        ListOperations<String, Object> listOperations = mock(ListOperations.class);
        SetOperations<String, Object> setOperations = mock(SetOperations.class);
        List<String> keys = List.of("missing:1", "missing:2");
        when(redisTemplate.delete(keys)).thenReturn(null);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.delete("users", "u1")).thenReturn(null);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPush("users", "u1")).thenReturn(null);
        when(listOperations.rightPush("users", "u1")).thenReturn(null);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add("users", "u1")).thenReturn(null);
        RedisFacade redisFacade = new RedisFacade(redisTemplate);

        assertThat(redisFacade.delete(keys)).isZero();
        assertThat(redisFacade.hdel("users", "u1")).isZero();
        assertThat(redisFacade.lpush("users", "u1")).isZero();
        assertThat(redisFacade.rpush("users", "u1")).isZero();
        assertThat(redisFacade.sadd("users", "u1")).isZero();
    }

    record TestUser(String id, String name) {
    }
}
