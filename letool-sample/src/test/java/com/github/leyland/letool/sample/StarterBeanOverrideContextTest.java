package com.github.leyland.letool.sample;

import com.github.leyland.letool.log.config.LogAutoConfiguration;
import com.github.leyland.letool.thread.config.ThreadPoolAutoConfiguration;
import com.github.leyland.letool.thread.propagation.MdcTaskDecorator;
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
            ));

    private final ApplicationContextRunner userOverrideContextRunner = contextRunner
            .withUserConfiguration(UserOverrideConfiguration.class);

    /**
     * 验证日志与线程 starter 默认并存时，只由线程模块提供一个 MDC 装饰器。
     */
    @Test
    void startersShouldShareThreadMdcTaskDecoratorByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(TaskDecorator.class))
                    .containsOnlyKeys("mdcTaskDecorator");
            assertThat(context.getBean(TaskDecorator.class))
                    .isInstanceOf(MdcTaskDecorator.class);
        });
    }

    /**
     * 验证用户自定义 {@link TaskDecorator}、{@code letoolTaskExecutor} 和
     * {@code letoolIoExecutor} 时，starter 不会创建重复执行器，并保留内置 MDC 装饰器。
     */
    @Test
    void startersShouldBackOffWhenUserProvidesTaskDecoratorAndExecutors() {
        userOverrideContextRunner.run(context -> {
            assertThat(context.getBeansOfType(TaskDecorator.class))
                    .containsOnlyKeys("userTaskDecorator", "mdcTaskDecorator");
            assertThat(context).hasBean("letoolTaskExecutor");
            assertThat(context).hasBean("letoolIoExecutor");
            assertThat(context.getBean("letoolTaskExecutor"))
                    .isSameAs(context.getBean("userTaskExecutor"));
            assertThat(context.getBean("letoolIoExecutor"))
                    .isSameAs(context.getBean("userIoExecutor"));
        });
    }

    /**
     * 模拟业务项目自行提供线程装饰器和执行器的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserOverrideConfiguration {

        /**
         * 创建用户自定义的任务装饰器。
         *
         * @return 用户任务装饰器
         */
        @Bean("userTaskDecorator")
        TaskDecorator taskDecorator() {
            return runnable -> runnable;
        }

        /**
         * 创建用户自定义的默认任务执行器。
         *
         * @return 用户任务执行器
         */
        @Bean({"letoolTaskExecutor", "userTaskExecutor"})
        ExecutorService taskExecutor() {
            return Executors.newSingleThreadExecutor();
        }

        /**
         * 创建用户自定义的 IO 任务执行器。
         *
         * @return 用户 IO 执行器
         */
        @Bean({"letoolIoExecutor", "userIoExecutor"})
        ExecutorService ioExecutor() {
            return Executors.newSingleThreadExecutor();
        }
    }
}
