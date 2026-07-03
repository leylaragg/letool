package com.github.leyland.letool.thread.config;

import com.github.leyland.letool.thread.monitor.ThreadPoolMonitor;
import com.github.leyland.letool.thread.pool.ThreadPoolManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ThreadPoolAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖用户自定义基础设施 Bean 时 starter 是否正确退让，避免默认 Bean
 * 与业务 Bean 同时存在造成注入歧义。</p>
 */
class ThreadPoolAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ThreadPoolAutoConfiguration.class));

    /**
     * 验证用户只提供 {@link ThreadPoolManager} 时，自动配置复用该管理器，
     * 并基于它继续创建默认的 {@link ThreadPoolMonitor}。
     */
    @Test
    void shouldUseUserThreadPoolManagerWhenProvided() {
        contextRunner
                .withUserConfiguration(UserManagerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadPoolManager.class);
                    assertThat(context).hasSingleBean(ThreadPoolMonitor.class);
                    assertThat(context.getBean(ThreadPoolManager.class))
                            .isSameAs(context.getBean("userThreadPoolManager"));
                });
    }

    /**
     * 验证用户同时提供 {@link ThreadPoolManager} 和 {@link ThreadPoolMonitor} 时，
     * 自动配置不会再创建同类型默认 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesManagerAndMonitor() {
        contextRunner
                .withUserConfiguration(UserManagerAndMonitorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadPoolManager.class);
                    assertThat(context).hasSingleBean(ThreadPoolMonitor.class);
                    assertThat(context.getBean(ThreadPoolManager.class))
                            .isSameAs(context.getBean("userThreadPoolManager"));
                    assertThat(context.getBean(ThreadPoolMonitor.class))
                            .isSameAs(context.getBean("userThreadPoolMonitor"));
                });
    }

    /**
     * 仅提供线程池管理器的用户侧配置。
     */
    /**
     * Disabling MDC propagation should keep thread pools available without a task decorator.
     */
    @Test
    void shouldDisableMdcTaskDecoratorWhenMdcPropagationIsDisabled() {
        contextRunner
                .withPropertyValues("letool.thread.context-propagation.mdc=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadPoolManager.class);
                    assertThat(context).hasBean("taskExecutor");
                    assertThat(context).hasBean("ioExecutor");
                    assertThat(context).doesNotHaveBean(TaskDecorator.class);
                    assertThat(context).doesNotHaveBean("mdcTaskDecorator");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserManagerConfiguration {

        @Bean
        ThreadPoolManager userThreadPoolManager() {
            return new ThreadPoolManager();
        }
    }

    /**
     * 同时提供线程池管理器和监控器的用户侧配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserManagerAndMonitorConfiguration {

        @Bean
        ThreadPoolManager userThreadPoolManager() {
            return new ThreadPoolManager();
        }

        @Bean
        ThreadPoolMonitor userThreadPoolMonitor(ThreadPoolManager userThreadPoolManager) {
            return new ThreadPoolMonitor(userThreadPoolManager, false);
        }
    }
}
