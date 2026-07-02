package com.github.leyland.letool.sample;

import com.github.leyland.letool.log.config.LogAutoConfiguration;
import com.github.leyland.letool.thread.config.ThreadPoolAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多 starter 并存时的用户 Bean 覆盖测试。
 *
 * <p>该测试模拟业务项目主动声明通用基础设施 Bean，确保 log/thread 等 starter
 * 在同一个 Spring 上下文中遵守自动配置退让规则。</p>
 */
class StarterBeanOverrideContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LogAutoConfiguration.class,
                    ThreadPoolAutoConfiguration.class
            ))
            .withUserConfiguration(UserOverrideConfiguration.class);

    /**
     * 验证用户自定义 {@link TaskDecorator}、{@code taskExecutor} 和 {@code ioExecutor} 时，
     * starter 不会创建重复 Bean。
     */
    @Test
    void startersShouldBackOffWhenUserProvidesTaskDecoratorAndExecutors() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskDecorator.class);
            assertThat(context).hasBean("taskExecutor");
            assertThat(context).hasBean("ioExecutor");
            assertThat(context.getBean(TaskDecorator.class)).isSameAs(context.getBean("userTaskDecorator"));
            assertThat(context.getBean("taskExecutor")).isSameAs(context.getBean("userTaskExecutor"));
            assertThat(context.getBean("ioExecutor")).isSameAs(context.getBean("userIoExecutor"));
        });
    }

    /**
     * 模拟业务项目自行提供线程装饰器和执行器的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserOverrideConfiguration {

        @Bean("userTaskDecorator")
        TaskDecorator taskDecorator() {
            return runnable -> runnable;
        }

        @Bean({"taskExecutor", "userTaskExecutor"})
        ExecutorService taskExecutor() {
            return Executors.newSingleThreadExecutor();
        }

        @Bean({"ioExecutor", "userIoExecutor"})
        ExecutorService ioExecutor() {
            return Executors.newSingleThreadExecutor();
        }
    }
}
