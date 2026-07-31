package com.github.leyland.letool.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Letool 限流模块配置。
 *
 * <p>本模块只负责将简洁的命名策略转换为 Sentinel 本地静态规则。
 * 生产环境若通过 Nacos、Apollo 或 Sentinel 控制台管理动态规则，应关闭
 * {@link #localRulesEnabled}，由外部 Sentinel 数据源接管规则生命周期。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.rate-limiter")
public class RateLimiterProperties {

    /**
     * 是否启用 Letool 限流模块。
     */
    private boolean enabled = true;

    /**
     * 未显式指定策略时使用的默认策略名称。
     */
    private String defaultPolicy = "default";

    /**
     * 是否根据本配置注册 Sentinel 本地静态规则。
     */
    private boolean localRulesEnabled = true;

    /**
     * 命名限流策略。
     */
    private Map<String, Policy> policies = defaultPolicies();

    /**
     * 声明式限流配置。
     */
    private AnnotationConfig annotation = new AnnotationConfig();

    /**
     * 创建默认策略集合。
     *
     * @return 包含默认策略的可变映射
     */
    private static Map<String, Policy> defaultPolicies() {
        Map<String, Policy> defaults = new LinkedHashMap<>();
        defaults.put("default", new Policy());
        return defaults;
    }

    /**
     * 判断模块是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置模块开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认策略名称。
     *
     * @return 默认策略名称
     */
    public String getDefaultPolicy() {
        return defaultPolicy;
    }

    /**
     * 设置默认策略名称。
     *
     * @param defaultPolicy 默认策略名称
     */
    public void setDefaultPolicy(String defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    /**
     * 判断是否启用本地静态规则。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isLocalRulesEnabled() {
        return localRulesEnabled;
    }

    /**
     * 设置是否启用本地静态规则。
     *
     * @param localRulesEnabled 是否启用本地静态规则
     */
    public void setLocalRulesEnabled(boolean localRulesEnabled) {
        this.localRulesEnabled = localRulesEnabled;
    }

    /**
     * 获取命名策略。
     *
     * @return 可变的命名策略映射
     */
    public Map<String, Policy> getPolicies() {
        return policies;
    }

    /**
     * 设置命名策略。
     *
     * @param policies 命名策略映射
     */
    public void setPolicies(Map<String, Policy> policies) {
        this.policies = policies;
    }

    /**
     * 获取声明式限流配置。
     *
     * @return 声明式限流配置
     */
    public AnnotationConfig getAnnotation() {
        return annotation;
    }

    /**
     * 设置声明式限流配置。
     *
     * @param annotation 声明式限流配置
     */
    public void setAnnotation(AnnotationConfig annotation) {
        this.annotation = annotation;
    }

    /**
     * 单个命名限流策略。
     */
    public static class Policy {

        /**
         * 每秒允许通过的许可数量。
         */
        private double threshold = 10D;

        /**
         * 获取每秒许可阈值。
         *
         * @return 每秒许可阈值
         */
        public double getThreshold() {
            return threshold;
        }

        /**
         * 设置每秒许可阈值。
         *
         * @param threshold 每秒许可阈值
         */
        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }
    }

    /**
     * 声明式限流开关。
     */
    public static class AnnotationConfig {

        /**
         * 是否启用 {@code @RateLimit} 切面。
         */
        private boolean enabled = true;

        /**
         * 判断声明式限流是否启用。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置声明式限流开关。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
