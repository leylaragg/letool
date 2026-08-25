package io.github.leylaragg.letool.cache.consistency;

import io.github.leylaragg.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Redis 写入围栏存储测试。
 */
@DisplayName("Redis 缓存写入围栏")
class RedisCacheFenceStoreTest {

    @Test
    @DisplayName("成功建立围栏后返回 token")
    void shouldReturnFenceAfterAcquire() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        whenScript(redisUtil).thenReturn(1L);
        RedisCacheFenceStore store = new RedisCacheFenceStore(redisUtil, "test:", Duration.ofMinutes(2));

        CacheFence fence = store.acquire("users", "u1", "event-1");

        assertEquals("users", fence.cacheName());
        assertEquals("u1", fence.serializedKey());
        assertEquals("event-1", fence.eventId());
    }

    @Test
    @DisplayName("其它写事务持有围栏时拒绝修改")
    void shouldRejectCompetingFence() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        whenScript(redisUtil).thenReturn(0L);
        RedisCacheFenceStore store = new RedisCacheFenceStore(redisUtil, "test:", Duration.ofMinutes(2));

        assertThrows(CacheFenceUnavailableException.class,
                () -> store.acquire("users", "u1", "event-1"));
    }

    @Test
    @DisplayName("Redis 状态无法确认时读取必须返回 UNKNOWN")
    void shouldReturnUnknownWhenRedisReadFails() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        whenScript(redisUtil).thenThrow(new IllegalStateException("redis down"));
        RedisCacheFenceStore store = new RedisCacheFenceStore(redisUtil, "test:", Duration.ofMinutes(2));

        assertEquals(CacheFenceState.UNKNOWN, store.state("users", "u1"));
    }

    @Test
    @DisplayName("只有 token 匹配的事件才能完成清理")
    void shouldCompleteOnlyMatchingFence() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        RedisTemplate<String, Object> redisTemplate = scriptTemplate(redisUtil);
        whenScript(redisTemplate).thenReturn(1L);
        RedisCacheFenceStore store = new RedisCacheFenceStore(redisUtil, "test:", Duration.ofMinutes(2));
        CacheFence fence = store.acquire("users", "u1", "event-1");
        assertEquals(CacheFenceCompletion.COMPLETED, store.complete(fence));
        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(
                any(RedisScript.class), any(RedisSerializer.class),
                any(RedisSerializer.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("已经完成的事件重复执行应返回幂等完成")
    void shouldRecognizeAlreadyCompletedEvent() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        whenScript(redisUtil).thenReturn(2L);
        RedisCacheFenceStore store = new RedisCacheFenceStore(redisUtil, "test:", Duration.ofMinutes(2));
        CacheFence fence = new CacheFence("users", "u1", "event-1", "token-1", java.time.Instant.now());

        assertEquals(CacheFenceCompletion.ALREADY_COMPLETED, store.complete(fence));
    }

    /** 创建缓存脚本使用的 RedisTemplate mock。 */
    @SuppressWarnings("unchecked")
    private static RedisTemplate<String, Object> scriptTemplate(RedisUtil redisUtil) {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(redisUtil.getTemplate()).thenReturn(redisTemplate);
        return redisTemplate;
    }

    private static OngoingStubbing<Object> whenScript(RedisUtil redisUtil) {
        return whenScript(scriptTemplate(redisUtil));
    }

    /** 对统一 Lua 执行入口配置返回值或异常。 */
    private static OngoingStubbing<Object> whenScript(
            RedisTemplate<String, Object> redisTemplate) {
        return when(redisTemplate.execute(
                any(RedisScript.class), any(RedisSerializer.class),
                any(RedisSerializer.class), anyList(), any(Object[].class)));
    }
}
