package com.github.leyland.letool.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 监控模块配置属性 —— 对应 application.yml 中 {@code letool.monitor} 前缀.
 *
 * <h3>示例配置</h3>
 * <pre>{@code
 * letool.monitor:
 *   enabled: true
 *   metrics:
 *     enabled: true
 *     export-prometheus: true
 *     step: 1m
 *   jvm:
 *     enabled: true
 *     collect-interval: 30s
 *   http:
 *     enabled: true
 *     include-paths: /**
 *   api-stats:
 *     enabled: true
 *     window-size: 60
 *     retention-days: 7
 *   alert:
 *     enabled: true
 *     dingtalk:
 *       webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
 *       secret: SECxxx
 *     wechat:
 *       webhook-url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
 *     mail:
 *       to:
 *         - admin@example.com
 *         - dev@example.com
 *   data-retention:
 *     enabled: false
 *     audit-log: 90d
 *     request-log: 30d
 *     api-stats: 7d
 *     api-error: 60d
 *     clean-cron: "0 0 3 * * ?"
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.monitor")
public class MonitorProperties {

    // ======================== 基础开关 ========================

    /** 监控模块总开关 —— false 则禁用整个监控模块 */
    private boolean enabled = true;

    // ======================== 子配置 ========================

    /** 指标采集配置 */
    private final Metrics metrics = new Metrics();

    /** JVM 监控配置 */
    private final Jvm jvm = new Jvm();

    /** HTTP 请求监控配置 */
    private final Http http = new Http();

    /** API 调用统计配置 */
    private final ApiStats apiStats = new ApiStats();

    /** 告警通知配置 */
    private final Alert alert = new Alert();

    /** 数据保留与清理配置 */
    private final DataRetention dataRetention = new DataRetention();

    // ======================== Getter / Setter ========================

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Metrics getMetrics() { return metrics; }

    public Jvm getJvm() { return jvm; }

    public Http getHttp() { return http; }

    public ApiStats getApiStats() { return apiStats; }

    public Alert getAlert() { return alert; }

    public DataRetention getDataRetention() { return dataRetention; }

    // ======================== 内部类：指标采集配置 ========================

    /**
     * 指标采集配置 —— 控制 Micrometer/Prometheus 指标的采集策略.
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class Metrics {

        /** 指标采集开关 */
        private boolean enabled = true;

        /** 是否导出 Prometheus 格式指标（/actuator/prometheus） */
        private boolean exportPrometheus = true;

        /** 指标采集步长，如 1m / 30s / 10s */
        private String step = "1m";

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isExportPrometheus() { return exportPrometheus; }

        public void setExportPrometheus(boolean exportPrometheus) { this.exportPrometheus = exportPrometheus; }

        public String getStep() { return step; }

        public void setStep(String step) { this.step = step; }
    }

    // ======================== 内部类：JVM 监控配置 ========================

    /**
     * JVM 监控配置 —— 控制堆内存、线程、GC 等 JVM 指标的采集频率.
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class Jvm {

        /** JVM 监控开关 */
        private boolean enabled = true;

        /** 采集间隔，如 30s / 1m / 10s */
        private String collectInterval = "30s";

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCollectInterval() { return collectInterval; }

        public void setCollectInterval(String collectInterval) { this.collectInterval = collectInterval; }
    }

    // ======================== 内部类：HTTP 请求监控配置 ========================

    /**
     * HTTP 请求监控配置 —— 控制请求路径拦截范围.
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class Http {

        /** HTTP 监控开关 */
        private boolean enabled = true;

        /** 需要监控的路径模式，支持 Ant 风格通配符，如 /api/**、/order/** */
        private String includePaths = "/**";

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getIncludePaths() { return includePaths; }

        public void setIncludePaths(String includePaths) { this.includePaths = includePaths; }
    }

    // ======================== 内部类：API 调用统计配置 ========================

    /**
     * API 调用统计配置 —— 控制 API 调用数据的聚合窗口和保留时长.
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class ApiStats {

        /** API 统计开关 */
        private boolean enabled = true;

        /** 时间窗口大小（分钟），超过此窗口的数据将被丢弃 */
        private int windowSize = 60;

        /** 统计数据保留天数 */
        private int retentionDays = 7;

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getWindowSize() { return windowSize; }

        public void setWindowSize(int windowSize) { this.windowSize = windowSize; }

        public int getRetentionDays() { return retentionDays; }

        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    // ======================== 内部类：告警通知配置 ========================

    /**
     * 告警通知配置 —— 控制钉钉、企微、邮件三种告警渠道.
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class Alert {

        /** 告警总开关 */
        private boolean enabled = true;

        /** 钉钉机器人配置 */
        private final DingTalk dingtalk = new DingTalk();

        /** 企业微信机器人配置 */
        private final Wechat wechat = new Wechat();

        /** 邮件告警配置 */
        private final Mail mail = new Mail();

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public DingTalk getDingtalk() { return dingtalk; }

        public Wechat getWechat() { return wechat; }

        public Mail getMail() { return mail; }

        // ---- 钉钉配置 ----

        /**
         * 钉钉机器人通知配置.
         *
         * @author leyland
         * @since 2.0.0
         */
        public static class DingTalk {

            /** 钉钉 Webhook 地址 */
            private String webhookUrl;

            /** 钉钉签名密钥（加签模式） */
            private String secret;

            public String getWebhookUrl() { return webhookUrl; }

            public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

            public String getSecret() { return secret; }

            public void setSecret(String secret) { this.secret = secret; }
        }

        // ---- 企微配置 ----

        /**
         * 企业微信机器人通知配置.
         *
         * @author leyland
         * @since 2.0.0
         */
        public static class Wechat {

            /** 企微 Webhook 地址 */
            private String webhookUrl;

            public String getWebhookUrl() { return webhookUrl; }

            public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
        }

        // ---- 邮件配置 ----

        /**
         * 邮件告警通知配置.
         *
         * @author leyland
         * @since 2.0.0
         */
        public static class Mail {

            /** 告警邮件接收人列表 */
            private List<String> to = new ArrayList<>();

            public List<String> getTo() { return to; }

            public void setTo(List<String> to) { this.to = to; }
        }
    }

    // ======================== 内部类：数据保留与清理配置 ========================

    /**
     * 数据保留与清理配置 —— 控制各类型监控数据的保留时长和自动清理策略.
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class DataRetention {

        /** 是否启用数据清理调度器，默认关闭 */
        private boolean enabled = false;

        /** 审计日志保留时长，如 90d */
        private String auditLog = "90d";

        /** 请求日志保留时长，如 30d */
        private String requestLog = "30d";

        /** API 统计数据保留时长，如 7d */
        private String apiStats = "7d";

        /** API 异常数据保留时长，如 60d */
        private String apiError = "60d";

        /** 清理任务的 Cron 表达式，默认每天凌晨 3:00 执行 */
        private String cleanCron = "0 0 3 * * ?";

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getAuditLog() { return auditLog; }

        public void setAuditLog(String auditLog) { this.auditLog = auditLog; }

        public String getRequestLog() { return requestLog; }

        public void setRequestLog(String requestLog) { this.requestLog = requestLog; }

        public String getApiStats() { return apiStats; }

        public void setApiStats(String apiStats) { this.apiStats = apiStats; }

        public String getApiError() { return apiError; }

        public void setApiError(String apiError) { this.apiError = apiError; }

        public String getCleanCron() { return cleanCron; }

        public void setCleanCron(String cleanCron) { this.cleanCron = cleanCron; }
    }
}
