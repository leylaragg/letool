package io.github.leylaragg.letool.monitor.config;

import io.github.leylaragg.letool.monitor.alert.AlertNotifier;
import io.github.leylaragg.letool.monitor.alert.DingTalkNotifier;
import io.github.leylaragg.letool.monitor.alert.WechatNotifier;
import io.github.leylaragg.letool.monitor.cleanup.CleanupTask;
import io.github.leylaragg.letool.monitor.cleanup.DataCleanupScheduler;
import io.github.leylaragg.letool.monitor.exception.MonitorErrorCode;
import io.github.leylaragg.letool.monitor.exception.MonitorException;
import io.github.leylaragg.letool.monitor.metrics.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

/**
 * monitor starter 自动配置。
 *
 * <p>指标存储、JVM 指标、HTTP 请求指标和导出端点由 Spring Boot Actuator 与
 * Micrometer 提供；本自动配置只补充 Letool 指标便利门面、告警分发器，以及
 * 用户 {@link CleanupTask} 的生产级调度能力。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration(after = {
        MetricsAutoConfiguration.class,
        CompositeMeterRegistryAutoConfiguration.class
})
@EnableConfigurationProperties(MonitorProperties.class)
@ConditionalOnProperty(
        prefix = "letool.monitor",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MonitorAutoConfiguration {

    /** 自动配置日志。 */
    private static final Logger log =
            LoggerFactory.getLogger(MonitorAutoConfiguration.class);

    /**
     * 注册基于应用 {@link MeterRegistry} 的指标便利门面。
     *
     * @param meterRegistry Spring Boot 管理的 Micrometer 注册表
     * @return Letool 指标便利门面
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.monitor.metrics",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public MetricsCollector metricsCollector(MeterRegistry meterRegistry) {
        log.info("[Monitor] 创建 MetricsCollector Bean");
        return new MetricsCollector(meterRegistry);
    }

    /**
     * 注册告警通知分发器，并按有效配置加载钉钉和企业微信渠道。
     *
     * @param properties monitor 模块配置
     * @return 告警通知分发器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.monitor.alert",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public AlertNotifier alertNotifier(MonitorProperties properties) {
        log.info("[Monitor] 创建 AlertNotifier Bean");
        AlertNotifier notifier = new AlertNotifier(properties);

        // 只有提供有效地址时才注册渠道，避免空配置产生不可用通知器。
        MonitorProperties.Alert.DingTalk dingTalk =
                properties.getAlert().getDingtalk();
        if (hasText(dingTalk.getWebhookUrl())) {
            notifier.registerChannel(new DingTalkNotifier(properties));
            log.info("[Monitor] 已注册钉钉告警渠道");
        }

        MonitorProperties.Alert.Wechat wechat =
                properties.getAlert().getWechat();
        if (hasText(wechat.getWebhookUrl())) {
            notifier.registerChannel(new WechatNotifier(properties));
            log.info("[Monitor] 已注册企业微信告警渠道");
        }
        return notifier;
    }

    /**
     * 注册并启动用户数据清理调度器。
     *
     * <p>启用清理后没有任何 {@link CleanupTask} 实现时会启动失败，防止把接口预留
     * 误认为已经执行真实数据清理。用户自定义同类型调度器时，本配置自动退让。</p>
     *
     * @param properties monitor 模块配置
     * @param cleanupTasks 用户提供的真实清理任务
     * @return 数据清理调度器
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.monitor.data-retention",
            name = "enabled",
            havingValue = "true")
    public DataCleanupScheduler dataCleanupScheduler(
            MonitorProperties properties,
            List<CleanupTask> cleanupTasks) {
        MonitorProperties.DataRetention retention =
                properties.getDataRetention();
        ZoneId zoneId = parseZoneId(retention.getZoneId());
        log.info(
                "[Monitor] 创建 DataCleanupScheduler Bean，cron={}，zone={}",
                retention.getCleanCron(),
                zoneId);
        return new DataCleanupScheduler(
                cleanupTasks,
                retention.getCleanCron(),
                zoneId,
                retention.getShutdownTimeout());
    }

    /**
     * 解析并校验用户配置的时区。
     *
     * @param zoneId 原始时区标识
     * @return 合法时区
     * @throws MonitorException 时区为空或不合法时抛出
     */
    private static ZoneId parseZoneId(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            throw MonitorException.of(
                    MonitorErrorCode.CONFIGURATION_INVALID,
                    "zoneId 不能为空");
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (DateTimeException exception) {
            throw MonitorException.causedBy(
                    MonitorErrorCode.CONFIGURATION_INVALID,
                    exception,
                    "zoneId 不合法：" + zoneId);
        }
    }

    /**
     * 判断字符串是否包含非空白内容。
     *
     * @param value 待判断字符串
     * @return 包含有效内容时返回 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
