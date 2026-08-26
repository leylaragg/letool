package io.github.leylaragg.letool.lock.config;

import io.github.leylaragg.letool.lock.aspect.IdempotentAspect;
import io.github.leylaragg.letool.lock.aspect.LockAspect;
import io.github.leylaragg.letool.lock.core.DistributedLock;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.lock.idempotent.IdempotentService;
import io.github.leylaragg.letool.lock.idempotent.IdempotentStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 验证通用锁自动配置只消费后端 SPI，不主动选择 Redis 等实现。 */
class LockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LockAutoConfiguration.class));

    /** 没有后端 SPI 时只绑定配置，不创建不可用的业务门面。 */
    @Test
    void shouldStayPassiveWithoutBackend() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LockProperties.class);
            assertThat(context).doesNotHaveBean(LockTemplate.class);
            assertThat(context).doesNotHaveBean(LockAspect.class);
            assertThat(context).doesNotHaveBean(IdempotentService.class);
            assertThat(context).doesNotHaveBean(IdempotentAspect.class);
        });
    }

    /** 后端同时提供锁与幂等 SPI 时应补齐通用模板和切面。 */
    @Test
    void shouldCreateGenericFacadesFromBackendSpis() {
        contextRunner.withUserConfiguration(BackendConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context).hasSingleBean(LockTemplate.class);
                    assertThat(context).hasSingleBean(LockAspect.class);
                    assertThat(context).hasSingleBean(IdempotentStore.class);
                    assertThat(context).hasSingleBean(IdempotentService.class);
                    assertThat(context).hasSingleBean(IdempotentAspect.class);
                });
    }

    /** 幂等开关关闭时不应影响锁模板。 */
    @Test
    void shouldDisableOnlyIdempotentFacade() {
        contextRunner.withUserConfiguration(BackendConfiguration.class)
                .withPropertyValues("letool.lock.idempotent.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(LockTemplate.class);
                    assertThat(context).doesNotHaveBean(IdempotentService.class);
                    assertThat(context).doesNotHaveBean(IdempotentAspect.class);
                });
    }

    /** 全局开关关闭时通用自动配置应完整退让。 */
    @Test
    void shouldDisableGenericAutoConfiguration() {
        contextRunner.withUserConfiguration(BackendConfiguration.class)
                .withPropertyValues("letool.lock.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LockProperties.class);
                    assertThat(context).doesNotHaveBean(LockTemplate.class);
                    assertThat(context).doesNotHaveBean(IdempotentService.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class BackendConfiguration {

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
