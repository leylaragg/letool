package com.github.leyland.letool.sms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 短信核心配置，绑定 {@code letool.sms}。
 */
@ConfigurationProperties(prefix = "letool.sms")
public class SmsProperties {

    private boolean enabled;
    private String defaultProvider;
    private Mock mock = new Mock();
    private RateLimit rateLimit = new RateLimit();

    /**
     * 判断短信模块是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置短信模块开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认 Provider。
     *
     * @return 默认 Provider；未配置时为 {@code null}
     */
    public String getDefaultProvider() {
        return defaultProvider;
    }

    /**
     * 设置默认 Provider。
     *
     * @param defaultProvider Provider 名称
     */
    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    /**
     * 获取 Mock 配置。
     *
     * @return Mock 配置
     */
    public Mock getMock() {
        return mock;
    }

    /**
     * 设置 Mock 配置。
     *
     * @param mock Mock 配置
     */
    public void setMock(Mock mock) {
        this.mock = mock;
    }

    /**
     * 获取限流配置。
     *
     * @return 限流配置
     */
    public RateLimit getRateLimit() {
        return rateLimit;
    }

    /**
     * 设置限流配置。
     *
     * @param rateLimit 限流配置
     */
    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    /**
     * Mock Provider 配置。
     */
    public static class Mock {

        private boolean enabled;

        /**
         * 判断 Mock Provider 是否启用。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置 Mock Provider 开关。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 本地发送尝试限流配置。
     */
    public static class RateLimit {

        private boolean enabled = true;
        private int maxPerMinute = 10;
        private int maxPerDay = 100;
        private long maximumTrackedPhones = 100_000L;

        /**
         * 判断本地限流是否启用。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置本地限流开关。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取单个手机号每分钟最多发送尝试次数。
         *
         * @return 每分钟上限
         */
        public int getMaxPerMinute() {
            return maxPerMinute;
        }

        /**
         * 设置单个手机号每分钟最多发送尝试次数。
         *
         * @param maxPerMinute 每分钟上限
         */
        public void setMaxPerMinute(int maxPerMinute) {
            this.maxPerMinute = maxPerMinute;
        }

        /**
         * 获取单个手机号每天最多发送尝试次数。
         *
         * @return 每日上限
         */
        public int getMaxPerDay() {
            return maxPerDay;
        }

        /**
         * 设置单个手机号每天最多发送尝试次数。
         *
         * @param maxPerDay 每日上限
         */
        public void setMaxPerDay(int maxPerDay) {
            this.maxPerDay = maxPerDay;
        }

        /**
         * 获取本地限流最多跟踪的手机号数量。
         *
         * @return 最大跟踪数量
         */
        public long getMaximumTrackedPhones() {
            return maximumTrackedPhones;
        }

        /**
         * 设置本地限流最多跟踪的手机号数量。
         *
         * @param maximumTrackedPhones 最大跟踪数量
         */
        public void setMaximumTrackedPhones(long maximumTrackedPhones) {
            this.maximumTrackedPhones = maximumTrackedPhones;
        }
    }
}
