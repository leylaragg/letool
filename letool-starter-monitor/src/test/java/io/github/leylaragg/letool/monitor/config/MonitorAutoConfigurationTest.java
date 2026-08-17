package io.github.leylaragg.letool.monitor.config;

import io.github.leylaragg.letool.monitor.alert.AlertNotifier;
import io.github.leylaragg.letool.monitor.cleanup.CleanupContext;
import io.github.leylaragg.letool.monitor.cleanup.CleanupTask;
import io.github.leylaragg.letool.monitor.cleanup.DataCleanupScheduler;
import io.github.leylaragg.letool.monitor.exception.MonitorException;
import io.github.leylaragg.letool.monitor.metrics.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MonitorAutoConfiguration} 生产装配边界测试。
 */
class MonitorAutoConfigurationTest {

    /** 包含 MeterRegistry 的默认测试上下文。 */
    private final ApplicationContextRunner contextRunner =
            baseRunner().withUserConfiguration(MeterRegistryConfiguration.class);

    /**
     * 验证默认创建 Micrometer 便利门面，但不创建默认关闭的清理调度器。
     */
    @Test
    void shouldCreateMetricsFacadeButNotCleanupSchedulerByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MonitorProperties.class);
            assertThat(context).hasSingleBean(MeterRegistry.class);
            assertThat(context).hasSingleBean(MetricsCollector.class);
            assertThat(context).hasSingleBean(AlertNotifier.class);
            assertThat(context).doesNotHaveBean(DataCleanupScheduler.class);
        });
    }

    /**
     * 验证没有 MeterRegistry 时不会创建不可用的指标门面。
     */
    @Test
    void shouldNotCreateMetricsFacadeWithoutMeterRegistry() {
        baseRunner().run(context -> {
            assertThat(context).hasSingleBean(MonitorProperties.class);
            assertThat(context).doesNotHaveBean(MetricsCollector.class);
        });
    }

    /**
     * 验证 Letool 自动配置在 Boot 默认 MeterRegistry 创建后再判断指标门面条件。
     */
    @Test
    void shouldCreateMetricsFacadeWithBootDefaultRegistry() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MetricsAutoConfiguration.class,
                        SimpleMetricsExportAutoConfiguration.class,
                        CompositeMeterRegistryAutoConfiguration.class,
                        MonitorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(MeterRegistry.class);
                    assertThat(context).hasSingleBean(MetricsCollector.class);
                });
    }

    /**
     * 验证关闭指标功能只影响指标便利门面。
     */
    @Test
    void shouldRespectMetricsSwitch() {
        contextRunner
                .withPropertyValues("letool.monitor.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MetricsCollector.class);
                    assertThat(context).hasSingleBean(AlertNotifier.class);
                });
    }

    /**
     * 验证关闭整个模块后不会创建任何 Monitor 组件。
     */
    @Test
    void shouldNotCreateBeansWhenMonitorDisabled() {
        contextRunner
                .withPropertyValues("letool.monitor.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MonitorProperties.class);
                    assertThat(context).doesNotHaveBean(MetricsCollector.class);
                    assertThat(context).doesNotHaveBean(AlertNotifier.class);
                    assertThat(context).doesNotHaveBean(
                            DataCleanupScheduler.class);
                });
    }

    /**
     * 验证用户提供 MetricsCollector 时默认门面会退让。
     */
    @Test
    void shouldBackOffForUserMetricsCollector() {
        contextRunner
                .withUserConfiguration(UserMetricsConfiguration.class)
                .run(context -> assertThat(
                        context.getBean(MetricsCollector.class))
                        .isSameAs(context.getBean("userMetricsCollector")));
    }

    /**
     * 验证显式启用清理但没有用户任务时应用启动失败。
     */
    @Test
    void shouldFailFastWhenCleanupEnabledWithoutTasks() {
        contextRunner
                .withPropertyValues(
                        "letool.monitor.data-retention.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(findMonitorException(
                            context.getStartupFailure()).getCode())
                            .isEqualTo("MONITOR_CLEANUP_TASK_MISSING");
                });
    }

    /**
     * 验证用户提供真实任务后会创建并启动清理调度器。
     */
    @Test
    void shouldCreateCleanupSchedulerForUserTasks() {
        contextRunner
                .withUserConfiguration(CleanupTaskConfiguration.class)
                .withPropertyValues(
                        "letool.monitor.data-retention.enabled=true",
                        "letool.monitor.data-retention.clean-cron=0 0 3 * * ?",
                        "letool.monitor.data-retention.zone-id=UTC",
                        "letool.monitor.data-retention.shutdown-timeout=2s")
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            DataCleanupScheduler.class);
                    assertThat(context.getBean(
                            DataCleanupScheduler.class).isRunning()).isTrue();
                });
    }

    /**
     * 验证非法 Cron 和时区不会静默回退。
     */
    @Test
    void shouldRejectInvalidCleanupConfiguration() {
        contextRunner
                .withUserConfiguration(CleanupTaskConfiguration.class)
                .withPropertyValues(
                        "letool.monitor.data-retention.enabled=true",
                        "letool.monitor.data-retention.clean-cron=invalid")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(findMonitorException(
                            context.getStartupFailure()).getCode())
                            .isEqualTo("MONITOR_CONFIGURATION_INVALID");
                });

        contextRunner
                .withUserConfiguration(CleanupTaskConfiguration.class)
                .withPropertyValues(
                        "letool.monitor.data-retention.enabled=true",
                        "letool.monitor.data-retention.zone-id=Invalid/Zone")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(findMonitorException(
                            context.getStartupFailure()).getCode())
                            .isEqualTo("MONITOR_CONFIGURATION_INVALID");
                });
    }

    /**
     * 验证用户提供 DataCleanupScheduler 时自动配置会退让。
     */
    @Test
    void shouldBackOffForUserCleanupScheduler() {
        contextRunner
                .withUserConfiguration(UserCleanupSchedulerConfiguration.class)
                .withPropertyValues(
                        "letool.monitor.data-retention.enabled=true")
                .run(context -> assertThat(
                        context.getBean(DataCleanupScheduler.class))
                        .isSameAs(context.getBean("userCleanupScheduler")));
    }

    /**
     * 创建只加载 Monitor 自动配置的上下文运行器。
     *
     * @return 基础上下文运行器
     */
    private static ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MonitorAutoConfiguration.class))
                .withPropertyValues(
                        "spring.main.allow-bean-definition-overriding=false");
    }

    /**
     * 从 Spring 启动失败原因链中查找监控异常。
     *
     * @param failure Spring 上下文启动异常
     * @return 原因链中的监控异常
     */
    private static MonitorException findMonitorException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof MonitorException monitorException) {
                return monitorException;
            }
            current = current.getCause();
        }
        throw new AssertionError("启动失败原因中没有 MonitorException", failure);
    }

    /**
     * 测试 MeterRegistry 配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        /**
         * 创建测试使用的 Micrometer 注册表。
         *
         * @return 内存注册表
         */
        @Bean(destroyMethod = "close")
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /**
     * 用户自定义指标门面配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserMetricsConfiguration {

        /**
         * 创建用户指标门面。
         *
         * @param meterRegistry 应用 MeterRegistry
         * @return 用户指标门面
         */
        @Bean
        MetricsCollector userMetricsCollector(MeterRegistry meterRegistry) {
            return new MetricsCollector(meterRegistry);
        }
    }

    /**
     * 用户真实清理任务配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class CleanupTaskConfiguration {

        /**
         * 创建不会访问外部系统的测试任务。
         *
         * @return 测试清理任务
         */
        @Bean
        CleanupTask testCleanupTask() {
            return new TestCleanupTask();
        }
    }

    /**
     * 用户自定义清理调度器配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserCleanupSchedulerConfiguration {

        /**
         * 创建用户清理任务。
         *
         * @return 测试任务
         */
        @Bean
        CleanupTask userCleanupTask() {
            return new TestCleanupTask();
        }

        /**
         * 创建用户清理调度器。
         *
         * @param userCleanupTask 用户清理任务
         * @return 用户调度器
         */
        @Bean
        DataCleanupScheduler userCleanupScheduler(
                CleanupTask userCleanupTask) {
            return new DataCleanupScheduler(
                    List.of(userCleanupTask),
                    "0 0 4 * * ?",
                    ZoneId.of("UTC"),
                    Duration.ofSeconds(2));
        }
    }

    /**
     * 自动配置测试使用的真实 CleanupTask 实现。
     */
    private static final class TestCleanupTask implements CleanupTask {

        /**
         * 获取测试任务名称。
         *
         * @return 任务名称
         */
        @Override
        public String name() {
            return "test-cleanup";
        }

        /**
         * 获取测试数据保留时长。
         *
         * @return 一天
         */
        @Override
        public Duration retention() {
            return Duration.ofDays(1);
        }

        /**
         * 执行测试清理。
         *
         * @param context 清理上下文
         * @return 零条记录
         */
        @Override
        public long cleanup(CleanupContext context) {
            return 0;
        }
    }
}
