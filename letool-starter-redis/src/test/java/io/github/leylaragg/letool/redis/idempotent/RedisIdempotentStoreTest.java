package io.github.leylaragg.letool.redis.idempotent;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Redis 幂等存储使用带 TTL 的原子 SET NX。 */
class RedisIdempotentStoreTest {

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
