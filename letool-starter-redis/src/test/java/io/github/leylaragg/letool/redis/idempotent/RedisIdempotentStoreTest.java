package io.github.leylaragg.letool.redis.idempotent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证 Redis 幂等存储使用带 TTL 的原子 SET NX。 */
class RedisIdempotentStoreTest {

    /**
     * 写入和撤销入口必须使用相同的幂等 key 边界，避免删除意外 Redis key。
     *
     * @param key null、空串或空白串
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void invalidKeyShouldBeRejectedConsistently(String key) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisIdempotentStore store = new RedisIdempotentStore(
                template, "letool:idempotent:");

        IllegalArgumentException putFailure = assertThrows(
                IllegalArgumentException.class,
                () -> store.putIfAbsent(key, Duration.ofMinutes(1)));
        IllegalArgumentException removeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> store.remove(key));

        assertEquals("幂等 key 不能为空", putFailure.getMessage());
        assertEquals(putFailure.getMessage(), removeFailure.getMessage());
        verifyNoInteractions(template);
    }

    /** Spring 返回 true 时表示本次成功占位。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldUseAtomicSetIfAbsentWithTtl() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(
                "letool:idempotent:pay:7", "DONE", Duration.ofHours(1))).thenReturn(true);

        RedisIdempotentStore store = new RedisIdempotentStore(
                template, "letool:idempotent:");

        assertTrue(store.putIfAbsent("pay:7", Duration.ofHours(1)));
        verify(values).setIfAbsent(
                "letool:idempotent:pay:7", "DONE", Duration.ofHours(1));
    }

    /** Spring 的 nullable Boolean 返回空时必须按未占位处理。 */
    @Test
    @SuppressWarnings("unchecked")
    void nullRedisResultShouldNotBeTreatedAsSuccess() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(
                "letool:idempotent:pay:7", "DONE", Duration.ofHours(1))).thenReturn(null);

        assertFalse(new RedisIdempotentStore(template, "letool:idempotent:")
                .putIfAbsent("pay:7", Duration.ofHours(1)));
    }
}
