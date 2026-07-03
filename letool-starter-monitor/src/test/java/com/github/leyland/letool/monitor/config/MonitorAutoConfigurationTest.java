package com.github.leyland.letool.monitor.config;

import com.github.leyland.letool.monitor.cleanup.DataCleanupScheduler;
import com.github.leyland.letool.monitor.metrics.JvmMetrics;
import com.github.leyland.letool.monitor.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MonitorAutoConfiguration} 的自动装配契约测试。
 *
 * <p>监控指标采集是本地工具能力，可以默认创建；数据清理当前只提供日志占位实现，
 * 必须显式开启，避免后台调度被误认为已执行真实 SQL 清理。</p>
 */
class MonitorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MonitorAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证默认启用监控指标，但不创建数据清理调度器。
     */
    @Test
    void shouldNotCreateDataCleanupSchedulerByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MetricsCollector.class);
            assertThat(context).doesNotHaveBean(DataCleanupScheduler.class);
        });
    }

    /**
     * 验证 JVM 指标采集器由 Bean 生命周期启动，初始化后已有首个快照。
     */
    @Test
    void shouldStartJvmMetricsWithBeanLifecycle() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JvmMetrics.class);
            assertThat(context.getBean(JvmMetrics.class).getMetrics()).isNotNull();
        });
    }

    /**
     * 验证显式开启数据保留清理后才会创建调度器。
     */
    @Test
    void shouldCreateDataCleanupSchedulerWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("letool.monitor.data-retention.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DataCleanupScheduler.class));
    }
}
