package io.github.leylaragg.letool.redis.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证缓存回源策略在进入执行链前固定全部时间和写入约束。 */
class RedisCachePolicyTest {

    /** 自定义策略应保留空值、抖动、锁等待和条件写入配置。 */
    @Test
    void shouldBuildCompletePolicy() {
        RedisCachePolicy<String> policy = RedisCachePolicy.<String>builder(Duration.ofMinutes(30))
                .cacheNull(Duration.ofMinutes(2))
                .ttlJitter(Duration.ofMinutes(5))
                .lockWait(Duration.ofSeconds(3))
                .cacheable(value -> value.startsWith("active:"))
                .build();

        assertTrue(policy.cacheNull());
        assertTrue(policy.cacheable().test("active:7"));
        assertFalse(policy.cacheable().test("disabled:7"));
    }

    /** 正常 TTL 与空值 TTL 必须为正，抖动和锁等待不能为负。 */
    @Test
    void shouldRejectInvalidDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> RedisCachePolicy.builder(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class,
                () -> RedisCachePolicy.builder(Duration.ofMinutes(1))
                        .cacheNull(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> RedisCachePolicy.builder(Duration.ofMinutes(1))
                        .ttlJitter(Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> RedisCachePolicy.builder(Duration.ofMinutes(1))
                        .lockWait(Duration.ofMillis(-1)));
    }
}
