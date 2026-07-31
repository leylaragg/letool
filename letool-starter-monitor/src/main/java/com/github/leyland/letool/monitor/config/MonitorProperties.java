package com.github.leyland.letool.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 监控模块配置属性，对应 {@code application.yml} 中的 {@code letool.monitor} 前缀。
 *
 * <p>指标采集与导出交由 Spring Boot Actuator 和 Micrometer 管理，本配置仅控制
 * Letool 提供的便利门面、告警渠道和用户清理任务调度。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.monitor")
public class MonitorProperties {

    /** 监控模块总开关。 */
    private boolean enabled = true;

    /** 指标便利门面配置。 */
    private final Metrics metrics = new Metrics();

    /** 告警通知配置。 */
    private final Alert alert = new Alert();

    /** 用户数据清理调度配置。 */
    private final DataRetention dataRetention = new DataRetention();

    /**
     * 判断是否启用监控模块。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置监控模块总开关。
     *
     * @param enabled 是否启用监控模块
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取指标便利门面配置。
     *
     * @return 指标配置
     */
    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * 获取告警通知配置。
     *
     * @return 告警配置
     */
    public Alert getAlert() {
        return alert;
    }

    /**
     * 获取用户数据清理调度配置。
     *
     * @return 数据清理配置
     */
    public DataRetention getDataRetention() {
        return dataRetention;
    }

    /**
     * Letool 指标便利门面配置。
     */
    public static class Metrics {

        /** 是否创建 Letool 的 {@code MetricsCollector} 便利门面。 */
        private boolean enabled = true;

        /**
         * 判断是否启用指标便利门面。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置指标便利门面开关。
         *
         * @param enabled 是否启用指标便利门面
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 告警通知配置。
     */
    public static class Alert {

        /** 告警总开关。 */
        private boolean enabled = true;

        /** 钉钉机器人配置。 */
        private final DingTalk dingtalk = new DingTalk();

        /** 企业微信机器人配置。 */
        private final Wechat wechat = new Wechat();

        /** 邮件告警接收人配置。 */
        private final Mail mail = new Mail();

        /**
         * 判断是否启用告警通知。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置告警通知总开关。
         *
         * @param enabled 是否启用告警通知
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取钉钉机器人配置。
         *
         * @return 钉钉配置
         */
        public DingTalk getDingtalk() {
            return dingtalk;
        }

        /**
         * 获取企业微信机器人配置。
         *
         * @return 企业微信配置
         */
        public Wechat getWechat() {
            return wechat;
        }

        /**
         * 获取邮件告警接收人配置。
         *
         * @return 邮件配置
         */
        public Mail getMail() {
            return mail;
        }

        /**
         * 钉钉机器人通知配置。
         */
        public static class DingTalk {

            /** 钉钉机器人 Webhook 地址。 */
            private String webhookUrl;

            /** 钉钉机器人加签密钥。 */
            private String secret;

            /**
             * 获取钉钉机器人 Webhook 地址。
             *
             * @return Webhook 地址
             */
            public String getWebhookUrl() {
                return webhookUrl;
            }

            /**
             * 设置钉钉机器人 Webhook 地址。
             *
             * @param webhookUrl Webhook 地址
             */
            public void setWebhookUrl(String webhookUrl) {
                this.webhookUrl = webhookUrl;
            }

            /**
             * 获取钉钉机器人加签密钥。
             *
             * @return 加签密钥
             */
            public String getSecret() {
                return secret;
            }

            /**
             * 设置钉钉机器人加签密钥。
             *
             * @param secret 加签密钥
             */
            public void setSecret(String secret) {
                this.secret = secret;
            }
        }

        /**
         * 企业微信机器人通知配置。
         */
        public static class Wechat {

            /** 企业微信机器人 Webhook 地址。 */
            private String webhookUrl;

            /**
             * 获取企业微信机器人 Webhook 地址。
             *
             * @return Webhook 地址
             */
            public String getWebhookUrl() {
                return webhookUrl;
            }

            /**
             * 设置企业微信机器人 Webhook 地址。
             *
             * @param webhookUrl Webhook 地址
             */
            public void setWebhookUrl(String webhookUrl) {
                this.webhookUrl = webhookUrl;
            }
        }

        /**
         * 邮件告警接收人配置。
         *
         * <p>monitor 模块只保存接收人列表，实际邮件发送能力由邮件模块或用户实现接入。</p>
         */
        public static class Mail {

            /** 告警邮件接收人列表。 */
            private List<String> to = new ArrayList<>();

            /**
             * 获取告警邮件接收人列表。
             *
             * @return 可变接收人列表
             */
            public List<String> getTo() {
                return to;
            }

            /**
             * 设置告警邮件接收人列表。
             *
             * @param to 接收人列表
             */
            public void setTo(List<String> to) {
                this.to = to == null ? new ArrayList<>() : new ArrayList<>(to);
            }
        }
    }

    /**
     * 用户数据清理调度配置。
     *
     * <p>模块不假设数据库类型和表结构，具体保留时长由每个 {@code CleanupTask}
     * 声明，当前配置只负责统一调度生命周期。</p>
     */
    public static class DataRetention {

        /** 是否启用用户数据清理调度，默认关闭。 */
        private boolean enabled;

        /** 清理任务的 Spring 六字段 Cron 表达式。 */
        private String cleanCron = "0 0 3 * * ?";

        /** Cron 表达式使用的时区标识。 */
        private String zoneId = ZoneId.systemDefault().getId();

        /** 应用关闭时等待清理线程完成的最长时间。 */
        private Duration shutdownTimeout = Duration.ofSeconds(10);

        /**
         * 判断是否启用用户数据清理调度。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置用户数据清理调度开关。
         *
         * @param enabled 是否启用数据清理调度
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取清理 Cron 表达式。
         *
         * @return Spring 六字段 Cron 表达式
         */
        public String getCleanCron() {
            return cleanCron;
        }

        /**
         * 设置清理 Cron 表达式。
         *
         * @param cleanCron Spring 六字段 Cron 表达式
         */
        public void setCleanCron(String cleanCron) {
            this.cleanCron = cleanCron;
        }

        /**
         * 获取 Cron 时区标识。
         *
         * @return 时区标识
         */
        public String getZoneId() {
            return zoneId;
        }

        /**
         * 设置 Cron 时区标识。
         *
         * @param zoneId 时区标识，例如 {@code Asia/Shanghai}
         */
        public void setZoneId(String zoneId) {
            this.zoneId = zoneId;
        }

        /**
         * 获取优雅关闭等待时间。
         *
         * @return 关闭等待时间
         */
        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        /**
         * 设置优雅关闭等待时间。
         *
         * @param shutdownTimeout 关闭等待时间
         */
        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }
    }
}
