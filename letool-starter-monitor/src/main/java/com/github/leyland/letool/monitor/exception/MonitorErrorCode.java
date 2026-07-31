package com.github.leyland.letool.monitor.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 监控模块稳定错误码。
 */
public enum MonitorErrorCode implements ErrorCode {

    /** 监控配置不合法。 */
    CONFIGURATION_INVALID(
            "MONITOR_CONFIGURATION_INVALID",
            "监控配置不合法：{0}"),

    /** 指标参数不合法。 */
    METRIC_ARGUMENT_INVALID(
            "MONITOR_METRIC_ARGUMENT_INVALID",
            "指标参数不合法：{0}"),

    /** 已启用数据清理但没有用户任务实现。 */
    CLEANUP_TASK_MISSING(
            "MONITOR_CLEANUP_TASK_MISSING",
            "已启用数据清理，但没有 CleanupTask 实现"),

    /** 清理任务名称重复。 */
    CLEANUP_TASK_DUPLICATED(
            "MONITOR_CLEANUP_TASK_DUPLICATED",
            "清理任务名称重复：{0}"),

    /** 数据清理调度启动失败。 */
    CLEANUP_SCHEDULE_FAILED(
            "MONITOR_CLEANUP_SCHEDULE_FAILED",
            "数据清理调度启动失败"),

    /** 用户清理任务执行失败。 */
    CLEANUP_TASK_FAILED(
            "MONITOR_CLEANUP_TASK_FAILED",
            "数据清理任务执行失败：{0}"),

    /** Webhook 告警投递失败。 */
    WEBHOOK_DELIVERY_FAILED(
            "MONITOR_WEBHOOK_DELIVERY_FAILED",
            "Webhook 告警投递失败：{0}");

    /** 稳定的外部错误码。 */
    private final String code;

    /** 不依赖 Spring 上下文的默认中文消息。 */
    private final String defaultMessage;

    /**
     * 创建监控错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认中文消息模板
     */
    MonitorErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认中文消息模板。
     *
     * @return 默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
