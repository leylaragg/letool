package io.github.leylaragg.letool.redis;

import io.github.leylaragg.letool.lock.core.DistributedLock;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis Starter 的依赖边界测试。
 *
 * <p>该测试用于约束模块必须直接具备 Spring Data Redis、Redisson 和后端无关锁契约，
 * 防止后续重构时依赖偶然的传递关系。</p>
 */
class RedisModuleBoundaryTest {

    /**
     * 验证 Redis Starter 对外能力所需的三类基础依赖都在当前模块可见。
     */
    @Test
    void moduleShouldExposeRedisRedissonAndLockContracts() {
        assertNotNull(RedisTemplate.class);
        assertNotNull(RedissonClient.class);
        assertNotNull(RLock.class);
        assertNotNull(DistributedLock.class);
    }
}
