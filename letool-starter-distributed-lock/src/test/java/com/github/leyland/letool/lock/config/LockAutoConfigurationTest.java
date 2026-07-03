package com.github.leyland.letool.lock.config;

import com.github.leyland.letool.lock.aspect.IdempotentAspect;
import com.github.leyland.letool.lock.aspect.LockAspect;
import com.github.leyland.letool.lock.core.DistributedLock;
import com.github.leyland.letool.lock.core.LockTemplate;
import com.github.leyland.letool.lock.core.RedisPessimisticLock;
import com.github.leyland.letool.lock.idempotent.IdempotentService;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Auto-configuration contract tests for {@link LockAutoConfiguration}.
 */
class LockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LockAutoConfiguration.class));

    /**
     * Lock runtime beans should stay passive until Redis infrastructure is present.
     */
    @Test
    void shouldStayPassiveWithoutRedisUtilBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LockProperties.class);
            assertThat(context).doesNotHaveBean(DistributedLock.class);
            assertThat(context).doesNotHaveBean(LockTemplate.class);
            assertThat(context).doesNotHaveBean(LockAspect.class);
            assertThat(context).doesNotHaveBean(IdempotentService.class);
            assertThat(context).doesNotHaveBean(IdempotentAspect.class);
        });
    }

    /**
     * Redis-backed lock beans should be created when RedisUtil is available.
     */
    @Test
    void shouldCreateRedisLockInfrastructureWhenRedisUtilBeanExists() {
        contextRunner.withUserConfiguration(RedisUtilConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context).hasSingleBean(RedisPessimisticLock.class);
                    assertThat(context).hasSingleBean(LockTemplate.class);
                    assertThat(context).hasSingleBean(LockAspect.class);
                    assertThat(context).hasSingleBean(IdempotentService.class);
                    assertThat(context).hasSingleBean(IdempotentAspect.class);
                });
    }

    /**
     * Idempotent infrastructure should be independently switchable.
     */
    @Test
    void shouldDisableIdempotentInfrastructureWhenConfiguredOff() {
        contextRunner.withUserConfiguration(RedisUtilConfiguration.class)
                .withPropertyValues("letool.lock.idempotent.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context).hasSingleBean(LockTemplate.class);
                    assertThat(context).hasSingleBean(LockAspect.class);
                    assertThat(context).doesNotHaveBean(IdempotentService.class);
                    assertThat(context).doesNotHaveBean(IdempotentAspect.class);
                    assertThat(context.getBean(LockProperties.class).getIdempotent().isEnabled()).isFalse();
                });
    }

    /**
     * Redis-backed defaults should not run when a different backend is requested.
     */
    @Test
    void shouldNotCreateRedisBackedInfrastructureWhenBackendIsNotRedis() {
        contextRunner.withUserConfiguration(RedisUtilConfiguration.class)
                .withPropertyValues("letool.lock.backend=zookeeper")
                .run(context -> {
                    assertThat(context).hasSingleBean(LockProperties.class);
                    assertThat(context.getBean(LockProperties.class).getBackend()).isEqualTo("zookeeper");
                    assertThat(context).doesNotHaveBean(DistributedLock.class);
                    assertThat(context).doesNotHaveBean(RedisPessimisticLock.class);
                    assertThat(context).doesNotHaveBean(LockTemplate.class);
                    assertThat(context).doesNotHaveBean(LockAspect.class);
                    assertThat(context).doesNotHaveBean(IdempotentService.class);
                    assertThat(context).doesNotHaveBean(IdempotentAspect.class);
                });
    }

    /**
     * User-provided lock infrastructure should win over starter defaults.
     */
    @Test
    void shouldBackOffWhenUserProvidesLockInfrastructure() {
        contextRunner.withUserConfiguration(UserLockInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context).hasSingleBean(LockTemplate.class);
                    assertThat(context).hasSingleBean(LockAspect.class);
                    assertThat(context).hasSingleBean(IdempotentService.class);
                    assertThat(context).hasSingleBean(IdempotentAspect.class);
                    assertThat(context.getBean(DistributedLock.class))
                            .isSameAs(context.getBean("userDistributedLock"));
                    assertThat(context.getBean(LockTemplate.class))
                            .isSameAs(context.getBean("userLockTemplate"));
                    assertThat(context.getBean(IdempotentService.class))
                            .isSameAs(context.getBean("userIdempotentService"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisUtilConfiguration {

        /**
         * Supplies RedisUtil without connecting to a real Redis server.
         *
         * @return mocked Redis helper.
         */
        @Bean
        RedisUtil redisUtil() {
            return mock(RedisUtil.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserLockInfrastructureConfiguration {

        /**
         * Supplies RedisUtil so default Redis-backed beans would be eligible.
         *
         * @return mocked Redis helper.
         */
        @Bean
        RedisUtil redisUtil() {
            return mock(RedisUtil.class);
        }

        /**
         * User-owned distributed lock implementation.
         *
         * @return no-op distributed lock for context tests.
         */
        @Bean
        DistributedLock userDistributedLock() {
            return new NoopDistributedLock();
        }

        /**
         * User-owned lock template.
         *
         * @param lock user distributed lock.
         * @return lock template using the user lock.
         */
        @Bean
        LockTemplate userLockTemplate(DistributedLock lock) {
            return new LockTemplate(lock);
        }

        /**
         * User-owned lock aspect.
         *
         * @param lockTemplate user lock template.
         * @return lock aspect using the user template.
         */
        @Bean
        LockAspect userLockAspect(LockTemplate lockTemplate) {
            return new LockAspect(lockTemplate);
        }

        /**
         * User-owned idempotent service.
         *
         * @return mocked idempotent service.
         */
        @Bean
        IdempotentService userIdempotentService() {
            return mock(IdempotentService.class);
        }

        /**
         * User-owned idempotent aspect.
         *
         * @param idempotentService user idempotent service.
         * @return idempotent aspect using the user service.
         */
        @Bean
        IdempotentAspect userIdempotentAspect(IdempotentService idempotentService) {
            return new IdempotentAspect(idempotentService);
        }
    }

    private static final class NoopDistributedLock implements DistributedLock {

        @Override
        public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
            return true;
        }

        @Override
        public void unlock(String key) {
            // No-op test lock.
        }

        @Override
        public boolean isLocked(String key) {
            return false;
        }
    }
}
