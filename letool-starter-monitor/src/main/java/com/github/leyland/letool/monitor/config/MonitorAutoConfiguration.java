package com.github.leyland.letool.monitor.config;

import com.github.leyland.letool.monitor.alert.AlertNotifier;
import com.github.leyland.letool.monitor.alert.DingTalkNotifier;
import com.github.leyland.letool.monitor.alert.WechatNotifier;
import com.github.leyland.letool.monitor.api.ApiErrorCollector;
import com.github.leyland.letool.monitor.api.ApiStatsAggregator;
import com.github.leyland.letool.monitor.api.ApiStatsCollector;
import com.github.leyland.letool.monitor.cleanup.DataCleanupScheduler;
import com.github.leyland.letool.monitor.metrics.JvmMetrics;
import com.github.leyland.letool.monitor.metrics.MetricsCollector;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * letool-starter-monitor 自动配置 —— 激活监控模块的所有 Spring 组件.
 *
 * <h3>自动注册的 Bean</h3>
 * <ul>
 *   <li>{@link MetricsCollector} —— Micrometer 风格通用指标采集器</li>
 *   <li>{@link JvmMetrics} —— JVM 指标采集器（通过 {@code @PostConstruct} 自动启动）</li>
 *   <li>{@link ApiStatsAggregator} —— API 统计聚合器</li>
 *   <li>{@link ApiStatsCollector} —— API 调用统计采集器（滑动窗口聚合引擎）</li>
 *   <li>{@link ApiErrorCollector} —— API 异常/错误采集器</li>
 *   <li>{@link AlertNotifier} —— 告警通知分发器（条件：{@code letool.monitor.alert.enabled=true}）</li>
 *   <li>{@link DingTalkNotifier} —— 钉钉通知渠道（条件：配置了 webhook-url）</li>
 *   <li>{@link WechatNotifier} —— 企业微信通知渠道（条件：配置了 webhook-url）</li>
 *   <li>{@link DataCleanupScheduler} —— 数据清理调度器（通过 {@code @PostConstruct} 自动启动）</li>
 * </ul>
 *
 * <h3>激活方式</h3>
 * <p>引入 {@code letool-starter-monitor} 依赖后自动激活（通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}），
 * 前提是 {@code letool.monitor.enabled} 不为 {@code false}。</p>
 *
 * <h3>生命周期</h3>
 * <p>{@link JvmMetrics} 和 {@link DataCleanupScheduler} 在 {@code @PostConstruct} 时启动，
 * 在 {@code @PreDestroy}（或 Context 关闭）时停止，确保资源正确释放。</p>
 *
 * <p>注意：使用 Spring Boot 3.x 的 {@link AutoConfiguration} 注解替代旧的 {@code @Configuration}，
 * 并通过 {@code AutoConfiguration.imports} 文件注册（替代旧的 {@code spring.factories}）.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(MonitorProperties.class)
@ConditionalOnProperty(prefix = "letool.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitorAutoConfiguration {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(MonitorAutoConfiguration.class);

    // ======================== 字段 ========================

    /** JVM 指标采集器引用（用于生命周期管理） */
    private JvmMetrics jvmMetrics;

    /** 数据清理调度器引用（用于生命周期管理） */
    private DataCleanupScheduler dataCleanupScheduler;

    // ======================== 指标采集 Bean ========================

    /**
     * 注册通用指标采集器 Bean.
     *
     * <p>Micrometer 风格的轻量级指标采集，提供计数器和计时器能力。</p>
     *
     * @return MetricsCollector 实例（单例）
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsCollector metricsCollector() {
        log.info("[Monitor] 创建 MetricsCollector Bean");
        return new MetricsCollector();
    }

    /**
     * 注册 JVM 指标采集器 Bean.
     *
     * <p>在 {@code @PostConstruct} 中自动启动定期采集。</p>
     *
     * @param properties 监控模块配置属性
     * @return JvmMetrics 实例（单例）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.monitor.jvm", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JvmMetrics jvmMetrics(MonitorProperties properties) {
        log.info("[Monitor] 创建 JvmMetrics Bean");
        jvmMetrics = new JvmMetrics(properties);
        return jvmMetrics;
    }

    // ======================== API 统计 Bean ========================

    /**
     * 注册 API 统计聚合器 Bean.
     *
     * @return ApiStatsAggregator 实例（单例）
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiStatsAggregator apiStatsAggregator() {
        log.info("[Monitor] 创建 ApiStatsAggregator Bean");
        return new ApiStatsAggregator();
    }

    /**
     * 注册 API 统计采集器 Bean.
     *
     * <p>滑动窗口大小由 {@code letool.monitor.api-stats.window-size} 配置决定。</p>
     *
     * @param properties 监控模块配置属性
     * @return ApiStatsCollector 实例（单例）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.monitor.api-stats", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApiStatsCollector apiStatsCollector(MonitorProperties properties) {
        int windowSize = properties.getApiStats().getWindowSize();
        log.info("[Monitor] 创建 ApiStatsCollector Bean，窗口大小: {} 分钟", windowSize);
        return new ApiStatsCollector(windowSize);
    }

    /**
     * 注册 API 错误采集器 Bean.
     *
     * @return ApiErrorCollector 实例（单例）
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiErrorCollector apiErrorCollector() {
        log.info("[Monitor] 创建 ApiErrorCollector Bean");
        return new ApiErrorCollector();
    }

    // ======================== 告警通知 Bean ========================

    /**
     * 注册告警通知分发器 Bean.
     *
     * <p>仅在 {@code letool.monitor.alert.enabled=true} 时创建。
     * 自动注册已配置的钉钉和企业微信通知渠道。</p>
     *
     * @param properties 监控模块配置属性
     * @return AlertNotifier 实例（单例）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.monitor.alert", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AlertNotifier alertNotifier(MonitorProperties properties) {
        log.info("[Monitor] 创建 AlertNotifier Bean");
        AlertNotifier notifier = new AlertNotifier(properties);

        // 条件注册钉钉渠道
        MonitorProperties.Alert.DingTalk dingTalk = properties.getAlert().getDingtalk();
        if (dingTalk.getWebhookUrl() != null && !dingTalk.getWebhookUrl().isEmpty()) {
            notifier.registerChannel(new DingTalkNotifier(properties));
            log.info("[Monitor] 已注册钉钉告警渠道");
        }

        // 条件注册企业微信渠道
        MonitorProperties.Alert.Wechat wechat = properties.getAlert().getWechat();
        if (wechat.getWebhookUrl() != null && !wechat.getWebhookUrl().isEmpty()) {
            notifier.registerChannel(new WechatNotifier(properties));
            log.info("[Monitor] 已注册企业微信告警渠道");
        }

        return notifier;
    }

    // ======================== 数据清理 Bean ========================

    /**
     * 注册数据清理调度器 Bean.
     *
     * <p>在 {@code @PostConstruct} 中自动启动定期清理调度。</p>
     *
     * @param properties 监控模块配置属性
     * @return DataCleanupScheduler 实例（单例）
     */
    @Bean
    @ConditionalOnMissingBean
    public DataCleanupScheduler dataCleanupScheduler(MonitorProperties properties) {
        log.info("[Monitor] 创建 DataCleanupScheduler Bean");
        dataCleanupScheduler = new DataCleanupScheduler(properties);
        return dataCleanupScheduler;
    }

    // ======================== 生命周期管理 ========================

    /**
     * 在 Bean 初始化完成后启动后台采集和调度任务.
     *
     * <p>使用 {@code @PostConstruct} 确保所有依赖注入完成后才启动。</p>
     */
    @PostConstruct
    public void startBackgroundTasks() {
        log.info("[Monitor] letool-starter-monitor 自动配置初始化完成，启动后台任务...");

        // 启动 JVM 指标采集
        if (jvmMetrics != null) {
            jvmMetrics.start();
        }

        // 启动数据清理调度
        if (dataCleanupScheduler != null) {
            dataCleanupScheduler.start();
        }
    }

    /**
     * 在容器销毁前停止后台任务，释放资源.
     */
    @PreDestroy
    public void stopBackgroundTasks() {
        log.info("[Monitor] 正在停止 letool-starter-monitor 后台任务...");

        if (jvmMetrics != null) {
            jvmMetrics.stop();
        }

        if (dataCleanupScheduler != null) {
            dataCleanupScheduler.stop();
        }

        log.info("[Monitor] letool-starter-monitor 后台任务已停止");
    }
}
