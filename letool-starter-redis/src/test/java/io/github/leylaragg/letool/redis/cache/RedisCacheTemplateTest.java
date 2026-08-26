package io.github.leylaragg.letool.redis.cache;

import io.github.leylaragg.letool.lock.core.LockRequest;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.lock.exception.LockException;
import io.github.leylaragg.letool.redis.exception.RedisOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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
    private LockTemplate lockTemplate;
    private RedisCacheTemplate template;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        lockTemplate = mock(LockTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        template = new RedisCacheTemplate(redisTemplate, lockTemplate, "cache:");
    }

    /** 命中缓存时不获取锁，也不调用数据源。 */
    @Test
    void hitShouldReturnCacheWithoutInvokingLoader() {
        when(values.get("user:7")).thenReturn(new User(7L, "Leyla", true));

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

    /** 数据源空值应写入短 TTL 哨兵，后续命中哨兵直接返回空。 */
    @Test
    void nullResultShouldUseNegativeCache() {
        when(values.get("user:404")).thenReturn(null).thenReturn(null);
        executeLockCallback();

        assertNull(template.getOrLoad(
                "user:404", User.class,
                RedisCachePolicy.<User>builder(Duration.ofMinutes(30))
                        .cacheNull(Duration.ofMinutes(2)).build(),
                () -> null));

        verify(values).set("user:404", RedisNullValue.INSTANCE, Duration.ofMinutes(2));
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

    private record User(long id, String name, boolean active) {
    }
}
