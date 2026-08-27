package io.github.leylaragg.letool.redis.cache;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import io.github.leylaragg.letool.lock.core.LockRequest;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.lock.exception.LockException;
import io.github.leylaragg.letool.redis.exception.RedisOperationException;
import io.github.leylaragg.letool.redis.serializer.FastJson2JsonRedisSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证单值缓存回源的命中、双检、穿透保护和竞争失败语义。 */
class RedisCacheTemplateTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> values;
    private RedisSerializer<Object> valueSerializer;
    private RedisConnection connection;
    private RedisStringCommands stringCommands;
    private Map<String, byte[]> rawValues;
    private LockTemplate lockTemplate;
    private RedisCacheTemplate template;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        valueSerializer = new FastJson2JsonRedisSerializer<>(Object.class);
        connection = mock(RedisConnection.class);
        stringCommands = mock(RedisStringCommands.class);
        rawValues = new HashMap<>();
        lockTemplate = mock(LockTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        org.mockito.Mockito.doReturn(RedisSerializer.string())
                .when(redisTemplate).getKeySerializer();
        org.mockito.Mockito.doReturn(valueSerializer)
                .when(redisTemplate).getValueSerializer();
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.get(any(byte[].class))).thenAnswer(invocation ->
                rawValues.get(new String(invocation.getArgument(0), StandardCharsets.UTF_8)));
        when(stringCommands.set(
                any(byte[].class), any(byte[].class),
                any(Expiration.class), eq(RedisStringCommands.SetOption.UPSERT)))
                .thenAnswer(invocation -> {
                    rawValues.put(
                            new String(invocation.getArgument(0), StandardCharsets.UTF_8),
                            invocation.getArgument(1));
                    return true;
                });
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation ->
                invocation.<RedisCallback<Object>>getArgument(0).doInRedis(connection));
        org.mockito.Mockito.doAnswer(invocation -> {
            rawValues.put(
                    invocation.getArgument(0),
                    valueSerializer.serialize(invocation.getArgument(1)));
            return null;
        }).when(values).set(anyString(), any(), any(Duration.class));
        template = new RedisCacheTemplate(redisTemplate, lockTemplate, "cache:");
    }

    /** 命中缓存时不获取锁，也不调用数据源。 */
    @Test
    void hitShouldReturnCacheWithoutInvokingLoader() {
        cacheValue("user:7", new User(7L, "Leyla", true));

        User actual = template.getOrLoad(
                "user:7", User.class, policy(), () -> {
                    throw new AssertionError("命中缓存时不得回源");
                });

        assertEquals("Leyla", actual.name());
        verifyNoInteractions(lockTemplate);
    }

    /** 未命中时应在锁内再次读取，并缓存数据源返回值。 */
    @Test
    void missShouldDoubleCheckAndCacheLoadedValue() {
        when(values.get("user:7")).thenReturn(null).thenReturn(null);
        executeLockCallback();

        User actual = template.getOrLoad(
                "user:7", User.class, policy(), () -> new User(7L, "Leyla", true));

        assertEquals("Leyla", actual.name());
        verify(values).set(eq("user:7"), eq(actual), any(Duration.class));
    }

    /** 数据源空值应写入短 TTL 协议标记，后续读取直接命中而不再回源。 */
    @Test
    void nullResultShouldUseNegativeCache() {
        executeLockCallback();
        AtomicInteger loads = new AtomicInteger();
        RedisCachePolicy<User> policy = RedisCachePolicy.<User>builder(Duration.ofMinutes(30))
                .cacheNull(Duration.ofMinutes(2)).build();

        assertNull(template.getOrLoad("user:404", User.class, policy, () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertNull(template.getOrLoad("user:404", User.class, policy, () -> {
            loads.incrementAndGet();
            return null;
        }));

        assertEquals(1, loads.get());
        verify(stringCommands).set(
                eq("user:404".getBytes(StandardCharsets.UTF_8)),
                any(byte[].class),
                eq(Expiration.from(Duration.ofMinutes(2))),
                eq(RedisStringCommands.SetOption.UPSERT));
    }

    /** 业务序列化器不保留枚举类型时，空值缓存仍应在第二次读取时稳定命中。 */
    @Test
    @SuppressWarnings("unchecked")
    void customSerializerShouldRecognizeCachedNullOnSecondRead() {
        String key = "user:404";
        AtomicReference<byte[]> storedValue = new AtomicReference<>();
        RedisSerializer<Object> businessSerializer = new AiZyStyleSerializer();
        RedisSerializer<String> keySerializer = RedisSerializer.string();
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands stringCommands = mock(RedisStringCommands.class);
        org.mockito.Mockito.doReturn(keySerializer).when(redisTemplate).getKeySerializer();
        org.mockito.Mockito.doReturn(businessSerializer).when(redisTemplate).getValueSerializer();
        template = new RedisCacheTemplate(redisTemplate, lockTemplate, "cache:");
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.get(any(byte[].class))).thenAnswer(invocation -> storedValue.get());
        when(stringCommands.set(
                any(byte[].class), any(byte[].class),
                any(Expiration.class), eq(RedisStringCommands.SetOption.UPSERT)))
                .thenAnswer(invocation -> {
                    storedValue.set(invocation.getArgument(1));
                    return true;
                });
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation ->
                invocation.<RedisCallback<Object>>getArgument(0).doInRedis(connection));
        when(values.get(key)).thenAnswer(invocation ->
                businessSerializer.deserialize(storedValue.get()));
        org.mockito.Mockito.doAnswer(invocation -> {
            storedValue.set(businessSerializer.serialize(invocation.getArgument(1)));
            return null;
        }).when(values).set(eq(key), any(), any(Duration.class));
        executeLockCallback();
        AtomicInteger loads = new AtomicInteger();
        Supplier<User> loader = () -> {
            loads.incrementAndGet();
            return null;
        };

        assertNull(template.getOrLoad(key, User.class, policy(), loader));
        assertNull(template.getOrLoad(key, User.class, policy(), loader));

        assertEquals(1, loads.get());
    }

    /** 被业务谓词拒绝的非空结果仍应返回，但不能写入缓存。 */
    @Test
    void rejectedValueShouldNotBeCached() {
        when(values.get("user:7")).thenReturn(null).thenReturn(null);
        executeLockCallback();
        User disabled = new User(7L, "Leyla", false);

        User actual = template.getOrLoad(
                "user:7", User.class,
                RedisCachePolicy.<User>builder(Duration.ofMinutes(30))
                        .doNotCacheNull().cacheable(User::active).build(),
                () -> disabled);

        assertSame(disabled, actual);
        verify(values, never()).set(anyString(), any(), any(Duration.class));
    }

    /** TTL 抖动只能增加正常 TTL，且不能超过配置上限。 */
    @Test
    void jitteredTtlShouldStayWithinConfiguredRange() {
        when(values.get("user:7")).thenReturn(null).thenReturn(null);
        executeLockCallback();
        RedisCachePolicy<User> policy = RedisCachePolicy.<User>builder(Duration.ofMinutes(30))
                .ttlJitter(Duration.ofMinutes(5)).build();

        template.getOrLoad("user:7", User.class, policy, () -> new User(7L, "Leyla", true));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("user:7"), any(User.class), ttl.capture());
        assertTrue(ttl.getValue().compareTo(Duration.ofMinutes(30)) >= 0);
        assertTrue(ttl.getValue().compareTo(Duration.ofMinutes(35)) <= 0);
    }

    /** 锁超时且最终仍未命中时不能绕过互斥直接访问数据源。 */
    @Test
    void lockTimeoutShouldNotBypassProtection() {
        when(values.get("user:7")).thenReturn(null).thenReturn(null);
        when(lockTemplate.execute(any(LockRequest.class), any(Supplier.class)))
                .thenThrow(new LockException("timeout"));
        AtomicInteger loads = new AtomicInteger();

        assertThrows(RedisOperationException.class,
                () -> template.getOrLoad("user:7", User.class, policy(), () -> {
                    loads.incrementAndGet();
                    return new User(7L, "Leyla", true);
                }));

        assertEquals(0, loads.get());
    }

    /** 数据源异常应原样传播，不能被误写为空值缓存。 */
    @Test
    void loaderFailureShouldPropagateWithoutCaching() {
        when(values.get("user:7")).thenReturn(null).thenReturn(null);
        executeLockCallback();
        IllegalStateException failure = new IllegalStateException("database unavailable");

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> template.getOrLoad("user:7", User.class, policy(), () -> {
                    throw failure;
                })));
        verify(values, never()).set(anyString(), any(), any(Duration.class));
    }

    private RedisCachePolicy<User> policy() {
        return RedisCachePolicy.<User>builder(Duration.ofMinutes(30))
                .cacheNull(Duration.ofMinutes(2))
                .lockWait(Duration.ofSeconds(3))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void executeLockCallback() {
        when(lockTemplate.execute(any(LockRequest.class), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.<Supplier<User>>getArgument(1).get());
    }

    private void cacheValue(String key, Object value) {
        rawValues.put(key, valueSerializer.serialize(value));
    }

    private record User(long id, String name, boolean active) {
    }

    /** 模拟 ai-zy 以 Object.class 读取 Fastjson2 数据的业务序列化器。 */
    private static final class AiZyStyleSerializer implements RedisSerializer<Object> {

        @Override
        public byte[] serialize(Object value) {
            if (value == null) {
                return new byte[0];
            }
            return JSON.toJSONString(value, JSONWriter.Feature.WriteClassName)
                    .getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object deserialize(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), Object.class);
        }
    }
}
