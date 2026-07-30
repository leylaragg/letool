package com.github.leyland.letool.thread.config;

import com.github.leyland.letool.thread.annotation.AsyncWithContext;
import com.github.leyland.letool.thread.monitor.ThreadPoolMonitor;
import com.github.leyland.letool.thread.pool.ThreadPoolManager;
import com.github.leyland.letool.thread.propagation.MdcTaskDecorator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
     * 验证关闭 MDC 传播后仍保留默认线程池，但不再注册任务装饰器。
     */
    @Test
    void shouldDisableMdcTaskDecoratorWhenMdcPropagationIsDisabled() {
        contextRunner
                .withPropertyValues("letool.thread.context-propagation.mdc=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadPoolManager.class);
                    assertThat(context).hasBean("letoolTaskExecutor");
                    assertThat(context).hasBean("letoolIoExecutor");
                    assertThat(context).doesNotHaveBean(TaskDecorator.class);
                    assertThat(context).doesNotHaveBean("mdcTaskDecorator");
                });
    }

    /**
     * 验证关闭监控子功能后不会注册无实际用途的监控 Bean。
     */
    @Test
    void shouldDisableThreadPoolMonitorWhenMonitoringIsDisabled() {
        contextRunner
                .withPropertyValues("letool.thread.monitoring.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadPoolManager.class);
                    assertThat(context).doesNotHaveBean(ThreadPoolMonitor.class);
                });
    }

    /**
     * 验证用户任务装饰器与模块 MDC 装饰器可以组合生效，而不是相互覆盖。
     */
    @Test
    void shouldComposeUserTaskDecoratorWithMdcDecorator() {
        contextRunner
                .withUserConfiguration(UserTaskDecoratorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MdcTaskDecorator.class);
                    assertThat(context).hasBean("userTaskDecorator");
                    AsyncTaskExecutor executor = context.getBean(
                            "letoolTaskExecutor",
                            AsyncTaskExecutor.class
                    );
                    MDC.put("traceId", "composed-context");
                    try {
                        DecoratorObservation observation = executor.submit(
                                () -> new DecoratorObservation(
                                        MDC.get("traceId"),
                                        UserTaskDecoratorConfiguration.USER_CONTEXT.get()
                                )
                        ).get(2, TimeUnit.SECONDS);

                        assertThat(observation.traceId()).isEqualTo("composed-context");
                        assertThat(observation.userContext()).isEqualTo("user-decorator");
                    } finally {
                        MDC.clear();
                    }
                });
    }

    /**
     * 验证用户可以关闭核心线程超时回收，而不是被管理器强制开启。
     */
    @Test
    void shouldHonorAllowCoreThreadTimeoutConfiguration() {
        contextRunner
                .withPropertyValues(
                        "letool.thread.pools.task-executor.allow-core-thread-timeout=false"
                )
                .run(context -> {
                    ThreadPoolManager manager = context.getBean(ThreadPoolManager.class);

                    assertThat(manager.get("letoolTaskExecutor").allowsCoreThreadTimeOut())
                            .isFalse();
                });
    }

    /**
     * 验证默认异步执行器通过 Spring 任务装饰能力传播 MDC 上下文。
     */
    @Test
    void taskExecutorShouldPropagateMdcContext() {
        contextRunner.run(context -> {
            AsyncTaskExecutor executor = context.getBean(
                    "letoolTaskExecutor",
                    AsyncTaskExecutor.class
            );
            MDC.put("traceId", "thread-context");
            try {
                String traceId = executor.submit(
                        () -> MDC.get("traceId")
                ).get(2, TimeUnit.SECONDS);

                assertThat(traceId).isEqualTo("thread-context");
            } finally {
                MDC.clear();
            }
        });
    }

    /**
     * 验证模块执行器与 Spring Boot 默认任务执行器并存，
     * 且不会抢占 MVC 和普通 {@code @Async} 使用的基础设施 Bean。
     */
    @Test
    void shouldCoexistWithBootTaskExecutionAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TaskExecutionAutoConfiguration.class,
                        ThreadPoolAutoConfiguration.class
                ))
                .run(context -> {
                    assertThat(context).hasBean("applicationTaskExecutor");
                    assertThat(context).hasBean("taskExecutor");
                    assertThat(context).hasBean("letoolTaskExecutor");
                    assertThat(context).hasBean("letoolIoExecutor");
                    assertThat(context.getBean("applicationTaskExecutor"))
                            .isSameAs(context.getBean("taskExecutor"));
                    assertThat(context.getBean("letoolTaskExecutor"))
                            .isNotSameAs(context.getBean("taskExecutor"));
                });
    }

    /**
     * 验证组合注解会经过 Spring 异步代理切换线程，并由默认执行器传播 MDC。
     */
    @Test
    void asyncWithContextShouldExecuteAsynchronouslyWithMdc() {
        contextRunner
                .withUserConfiguration(AsyncProbeConfiguration.class)
                .run(context -> {
                    String callerThread = Thread.currentThread().getName();
                    MDC.put("traceId", "async-context");
                    try {
                        AsyncObservation observation = context.getBean(AsyncProbe.class)
                                .capture()
                                .get(2, TimeUnit.SECONDS);

                        assertThat(observation.threadName()).isNotEqualTo(callerThread);
                        assertThat(observation.threadName()).startsWith("task-");
                        assertThat(observation.traceId()).isEqualTo("async-context");
                    } finally {
                        MDC.clear();
                    }
                });
    }

    /**
     * 仅提供线程池管理器的用户侧配置。
     */
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

    /**
     * 注册用于验证 Spring 异步代理的测试服务。
     */
    @Configuration(proxyBeanMethods = false)
    static class AsyncProbeConfiguration {

        /**
         * 创建异步调用探针。
         *
         * @return 异步调用探针
         */
        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    /**
     * 记录异步任务所在线程及其 MDC 的测试探针。
     */
    static class AsyncProbe {

        /**
         * 在默认异步执行器中捕获线程名和追踪标识。
         *
         * @return 已完成的异步观察结果
         */
        @AsyncWithContext
        public CompletableFuture<AsyncObservation> capture() {
            return CompletableFuture.completedFuture(
                    new AsyncObservation(
                            Thread.currentThread().getName(),
                            MDC.get("traceId")
                    )
            );
        }
    }

    /**
     * 异步调用观察结果。
     *
     * @param threadName 执行任务的线程名称
     * @param traceId 任务执行期间读取到的追踪标识
     */
    record AsyncObservation(String threadName, String traceId) {
    }

    /**
     * 注册用户自定义任务装饰器的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserTaskDecoratorConfiguration {

        /** 模拟用户希望传播的自定义线程上下文。 */
        private static final ThreadLocal<String> USER_CONTEXT = new ThreadLocal<>();

        /**
         * 创建用户任务装饰器。
         *
         * @return 在任务执行期间写入测试上下文的装饰器
         */
        @Bean
        TaskDecorator userTaskDecorator() {
            return runnable -> () -> {
                String previous = USER_CONTEXT.get();
                try {
                    USER_CONTEXT.set("user-decorator");
                    runnable.run();
                } finally {
                    if (previous == null) {
                        USER_CONTEXT.remove();
                    } else {
                        USER_CONTEXT.set(previous);
                    }
                }
            };
        }
    }

    /**
     * 组合任务装饰器的观察结果。
     *
     * @param traceId MDC 追踪标识
     * @param userContext 用户装饰器写入的上下文
     */
    record DecoratorObservation(String traceId, String userContext) {
    }
}
