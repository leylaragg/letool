package io.github.leylaragg.letool.redis.config;

import io.github.leylaragg.letool.lock.config.LockAutoConfiguration;
import io.github.leylaragg.letool.lock.core.DistributedLock;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.lock.idempotent.IdempotentService;
import io.github.leylaragg.letool.lock.idempotent.IdempotentStore;
import io.github.leylaragg.letool.redis.idempotent.RedisIdempotentStore;
import io.github.leylaragg.letool.redis.lock.RedissonDistributedLock;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 验证 Redis 后端 SPI 与通用锁自动配置可以组成完整调用链。 */
class LetoolRedisBackendAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LetoolRedisBackendAutoConfiguration.class,
                    LockAutoConfiguration.class));

    /** Redisson 与字符串模板存在时应同时提供锁和幂等实现。 */
    @Test
    void shouldCreateRedisBackendsAndGenericFacades() {
        contextRunner.withUserConfiguration(RedisInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context).hasSingleBean(RedissonDistributedLock.class);
                    assertThat(context).hasSingleBean(LockTemplate.class);
                    assertThat(context).hasSingleBean(IdempotentStore.class);
                    assertThat(context).hasSingleBean(RedisIdempotentStore.class);
                    assertThat(context).hasSingleBean(IdempotentService.class);
                });
    }

    /** 应用自定义后端必须使 Redis 默认实现完整退让。 */
    @Test
    void shouldBackOffForUserBackends() {
        contextRunner.withUserConfiguration(UserBackendConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context).doesNotHaveBean(RedissonDistributedLock.class);
                    assertThat(context).hasSingleBean(IdempotentStore.class);
                    assertThat(context).doesNotHaveBean(RedisIdempotentStore.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisInfrastructureConfiguration {
        @Bean
        RedissonClient redissonClient() {
            return mock(RedissonClient.class);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserBackendConfiguration extends RedisInfrastructureConfiguration {
        @Bean
        DistributedLock distributedLock() {
            return mock(DistributedLock.class);
        }

        @Bean
        IdempotentStore idempotentStore() {
            return mock(IdempotentStore.class);
        }
    }
}
