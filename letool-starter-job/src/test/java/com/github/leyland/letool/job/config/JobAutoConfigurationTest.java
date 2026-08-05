package com.github.leyland.letool.job.config;

import com.github.leyland.letool.job.core.JobHandlerRegistry;
import com.github.leyland.letool.job.core.DefaultJobHandlerRegistry;
import com.github.leyland.letool.job.core.JobScheduler;
import com.github.leyland.letool.job.core.LoggingJobLogService;
import com.github.leyland.letool.job.quartz.JobRuntime;
import com.github.leyland.letool.job.quartz.LetoolJobRegistrar;
import com.github.leyland.letool.job.quartz.QuartzJobMapper;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JobAutoConfiguration} Quartz 自动装配契约测试。
 */
class JobAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(QuartzAutoConfiguration.class, JobAutoConfiguration.class));

    /**
     * 验证默认配置复用 Spring Boot Scheduler 并注册 Letool 便利组件。
     */
    @Test
    void shouldCreateQuartzBackedJobInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Scheduler.class);
            assertThat(context).hasSingleBean(QuartzJobMapper.class);
            assertThat(context).hasSingleBean(JobHandlerRegistry.class);
            assertThat(context).hasSingleBean(JobRuntime.class);
            assertThat(context).hasSingleBean(JobScheduler.class);
            assertThat(context).hasSingleBean(LetoolJobRegistrar.class);
            assertThat(context).hasSingleBean(LoggingJobLogService.class);
            assertThat(context.getBean(Scheduler.class).getContext().get(JobRuntime.SCHEDULER_CONTEXT_KEY))
                    .isSameAs(context.getBean(JobRuntime.class));
        });
    }

    /**
     * 验证关闭 Letool Job 只关闭封装组件，不夺取原生 Quartz 生命周期。
     */
    @Test
    void shouldDisableOnlyLetoolJobInfrastructure() {
        contextRunner.withPropertyValues("letool.job.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(Scheduler.class);
            assertThat(context).doesNotHaveBean(JobScheduler.class);
            assertThat(context).doesNotHaveBean(JobRuntime.class);
        });
    }

    /**
     * 验证默认结构化日志可以独立关闭。
     */
    @Test
    void shouldDisableDefaultLoggingServiceIndependently() {
        contextRunner.withPropertyValues("letool.job.logging.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(JobRuntime.class);
            assertThat(context).doesNotHaveBean(LoggingJobLogService.class);
        });
    }

    /**
     * 验证用户注册表会使默认实现退让。
     */
    @Test
    void shouldBackOffForUserHandlerRegistry() {
        contextRunner.withUserConfiguration(UserRegistryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(JobHandlerRegistry.class);
            assertThat(context.getBean(JobHandlerRegistry.class))
                    .isSameAs(context.getBean("userJobHandlerRegistry"));
        });
    }

    /** 用户自定义注册表配置。 */
    @Configuration(proxyBeanMethods = false)
    static class UserRegistryConfiguration {

        /** @return 用户自定义处理器注册表 */
        @Bean
        JobHandlerRegistry userJobHandlerRegistry() {
            return new DefaultJobHandlerRegistry();
        }
    }
}
