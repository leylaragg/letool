package com.github.leyland.letool.monitor.config;

import com.github.leyland.letool.monitor.cleanup.DataCleanupScheduler;
import com.github.leyland.letool.monitor.alert.AlertNotifier;
import com.github.leyland.letool.monitor.api.ApiErrorCollector;
import com.github.leyland.letool.monitor.api.ApiStatsAggregator;
import com.github.leyland.letool.monitor.api.ApiStatsCollector;
import com.github.leyland.letool.monitor.metrics.JvmMetrics;
import com.github.leyland.letool.monitor.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            assertThat(context).hasSingleBean(MonitorProperties.class);
            assertThat(context).hasSingleBean(MetricsCollector.class);
            assertThat(context).doesNotHaveBean(DataCleanupScheduler.class);
        });
    }

    /**
     * 验证关闭监控模块后不会注册任何监控组件。
     */
    @Test
    void shouldNotCreateBeansWhenMonitorDisabled() {
        contextRunner
                .withPropertyValues("letool.monitor.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MonitorProperties.class);
                    assertThat(context).doesNotHaveBean(MetricsCollector.class);
                    assertThat(context).doesNotHaveBean(JvmMetrics.class);
                    assertThat(context).doesNotHaveBean(ApiStatsCollector.class);
                    assertThat(context).doesNotHaveBean(AlertNotifier.class);
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

    /**
     * 验证各子功能开关只影响对应工具组件。
     */
    @Test
    void shouldRespectFeatureSwitches() {
        contextRunner
                .withPropertyValues(
                        "letool.monitor.metrics.enabled=false",
                        "letool.monitor.jvm.enabled=false",
                        "letool.monitor.api-stats.enabled=false",
                        "letool.monitor.alert.enabled=false",
                        "letool.monitor.data-retention.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MetricsCollector.class);
                    assertThat(context).doesNotHaveBean(JvmMetrics.class);
                    assertThat(context).doesNotHaveBean(ApiStatsAggregator.class);
                    assertThat(context).doesNotHaveBean(ApiStatsCollector.class);
                    assertThat(context).doesNotHaveBean(ApiErrorCollector.class);
                    assertThat(context).doesNotHaveBean(AlertNotifier.class);
                    assertThat(context).doesNotHaveBean(DataCleanupScheduler.class);
                });
    }

    /**
     * 验证业务项目自定义监控组件时，自动配置会按类型回退。
     */
    @Test
    void shouldBackOffWhenUserProvidesMonitorBeans() {
        contextRunner
                .withUserConfiguration(UserMonitorConfiguration.class)
                .withPropertyValues("letool.monitor.data-retention.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(MetricsCollector.class)).isSameAs(context.getBean("metricsCollector"));
                    assertThat(context.getBean(JvmMetrics.class)).isSameAs(context.getBean("jvmMetrics"));
                    assertThat(context.getBean(ApiStatsAggregator.class)).isSameAs(context.getBean("apiStatsAggregator"));
                    assertThat(context.getBean(ApiStatsCollector.class)).isSameAs(context.getBean("apiStatsCollector"));
                    assertThat(context.getBean(ApiErrorCollector.class)).isSameAs(context.getBean("apiErrorCollector"));
                    assertThat(context.getBean(AlertNotifier.class)).isSameAs(context.getBean("alertNotifier"));
                    assertThat(context.getBean(DataCleanupScheduler.class)).isSameAs(context.getBean("dataCleanupScheduler"));
                });
    }

    /**
     * 用户侧监控组件配置，用于验证 starter 自动配置不会抢占用户 Bean。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserMonitorConfiguration {

        /**
         * 自定义通用指标采集器。
         *
         * @return 指标采集器实例
         */
        @Bean
        MetricsCollector metricsCollector() {
            return new MetricsCollector();
        }

        /**
         * 自定义 JVM 指标采集器。
         *
         * @param properties 监控配置属性
         * @return JVM 指标采集器实例
         */
        @Bean
        JvmMetrics jvmMetrics(MonitorProperties properties) {
            return new JvmMetrics(properties);
        }

        /**
         * 自定义 API 统计聚合器。
         *
         * @return API 统计聚合器实例
         */
        @Bean
        ApiStatsAggregator apiStatsAggregator() {
            return new ApiStatsAggregator();
        }

        /**
         * 自定义 API 统计采集器。
         *
         * @return API 统计采集器实例
         */
        @Bean
        ApiStatsCollector apiStatsCollector() {
            return new ApiStatsCollector(5);
        }

        /**
         * 自定义 API 错误采集器。
         *
         * @return API 错误采集器实例
         */
        @Bean
        ApiErrorCollector apiErrorCollector() {
            return new ApiErrorCollector();
        }

        /**
         * 自定义告警分发器。
         *
         * @param properties 监控配置属性
         * @return 告警分发器实例
         */
        @Bean
        AlertNotifier alertNotifier(MonitorProperties properties) {
            return new AlertNotifier(properties);
        }

        /**
         * 自定义数据清理调度器。
         *
         * @param properties 监控配置属性
         * @return 数据清理调度器实例
         */
        @Bean
        DataCleanupScheduler dataCleanupScheduler(MonitorProperties properties) {
            return new DataCleanupScheduler(properties);
        }
    }
}
