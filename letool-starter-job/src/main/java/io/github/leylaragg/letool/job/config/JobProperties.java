package io.github.leylaragg.letool.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Letool Job 自有配置。
 *
 * <p>Quartz 线程池、JobStore、数据源、集群和优雅停机继续使用
 * Spring Boot 原生 {@code spring.quartz.*} 配置。</p>
 */
@ConfigurationProperties(prefix = "letool.job")
public class JobProperties {

    private boolean enabled = true;
    private String group = "letool";
    private int errorSummaryMaxLength = 1_024;
    private Logging logging = new Logging();

    /** @return 是否启用 Letool Job 封装 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用 Letool Job 封装 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return Letool 管理的 Quartz 组名 */
    public String getGroup() {
        return group;
    }

    /**
     * 设置 Letool 管理的 Quartz 组名。
     *
     * @param group 非空 Quartz 组名
     */
    public void setGroup(String group) {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("letool.job.group 不能为空");
        }
        this.group = group.trim();
    }

    /** @return 执行错误摘要最大字符数 */
    public int getErrorSummaryMaxLength() {
        return errorSummaryMaxLength;
    }

    /**
     * 设置执行错误摘要最大字符数。
     *
     * @param errorSummaryMaxLength 大于零的最大字符数
     */
    public void setErrorSummaryMaxLength(int errorSummaryMaxLength) {
        if (errorSummaryMaxLength <= 0) {
            throw new IllegalArgumentException("letool.job.error-summary-max-length 必须大于 0");
        }
        this.errorSummaryMaxLength = errorSummaryMaxLength;
    }

    /** @return 默认结构化日志配置 */
    public Logging getLogging() {
        return logging;
    }

    /** @param logging 默认结构化日志配置 */
    public void setLogging(Logging logging) {
        this.logging = logging == null ? new Logging() : logging;
    }

    /**
     * 默认结构化日志配置。
     */
    public static class Logging {
        private boolean enabled = true;

        /** @return 是否启用默认结构化日志 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用默认结构化日志 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
