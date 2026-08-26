package io.github.leylaragg.letool.redis;

import io.github.leylaragg.letool.lock.core.LockRequest;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.redis.cache.RedisCacheTemplate;
import io.github.leylaragg.letool.redis.config.LetoolRedisProperties;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 RedisFacade 将业务入口委托给独立锁与缓存组件。 */
class RedisFacadeTest {

    /** 原生锁入口应统一应用配置的公平性和 key 前缀。 */
    @Test
    @SuppressWarnings("unchecked")
    void getLockShouldApplyConfiguredPrefixAndFairness() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedissonClient client = mock(RedissonClient.class);
        RLock expected = mock(RLock.class);
        LetoolRedisProperties properties = new LetoolRedisProperties();
        properties.getLock().setFair(true);
        properties.getLock().setKeyPrefix("biz:lock:");
        when(client.getFairLock("biz:lock:order:7")).thenReturn(expected);

        RedisFacade util = new RedisFacade(
                redisTemplate, client, null, null, properties);

        assertSame(expected, util.getLock("order:7"));
    }

    /** 函数式锁入口应使用默认等待时间和看门狗请求。 */
    @Test
    @SuppressWarnings("unchecked")
    void executeWithLockShouldDelegateToLockTemplate() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        LockTemplate lockTemplate = mock(LockTemplate.class);
        LetoolRedisProperties properties = new LetoolRedisProperties();
        properties.getCache().setLockWait(Duration.ofSeconds(5));
        when(lockTemplate.execute(any(LockRequest.class), any(Supplier.class))).thenReturn("ok");
        RedisFacade util = new RedisFacade(
                redisTemplate, null, lockTemplate, null, properties);

        assertEquals("ok", util.executeWithLock("order:7", () -> "ok"));

        verify(lockTemplate).execute(
                eq(LockRequest.watchdog("order:7", Duration.ofSeconds(5))),
                any(Supplier.class));
    }

    /** 缓存门面应将显式策略与加载器原样交给缓存模板。 */
    @Test
    @SuppressWarnings("unchecked")
    void getOrLoadShouldDelegateToCacheTemplate() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisCacheTemplate cacheTemplate = mock(RedisCacheTemplate.class);
        LetoolRedisProperties properties = new LetoolRedisProperties();
        Supplier<String> loader = () -> "db";
        when(cacheTemplate.getOrLoad(
                eq("user:7"), eq(String.class), any(), eq(loader))).thenReturn("cached");
        RedisFacade util = new RedisFacade(
                redisTemplate, null, null, cacheTemplate, properties);

        assertEquals("cached", util.getOrLoad(
                "user:7", String.class, Duration.ofMinutes(30), loader));
    }
}
