package com.github.leyland.letool.tool.function;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 重试工具对外暴露的稳定错误码。
 */
public enum RetryErrorCode implements ErrorCode {

    /** 重试任务、策略或策略参数不符合公开契约。 */
    INVALID_ARGUMENT("TOOL_RETRY_001", "重试参数无效：{0}"),

    /** 任务失败且异常不满足继续重试条件。 */
    EXECUTION_FAILED("TOOL_RETRY_002", "重试任务执行失败，已执行 {0} 次"),

    /** 异常或结果持续满足重试条件，最大尝试次数已经耗尽。 */
    EXHAUSTED("TOOL_RETRY_003", "重试次数已耗尽，共执行 {0} 次"),

    /** 任务执行或退避等待被线程中断。 */
    INTERRUPTED("TOOL_RETRY_004", "重试执行被中断，共执行 {0} 次");

    /** 稳定错误码。 */
    private final String code;

    /** 未配置国际化资源时使用的安全默认消息。 */
    private final String defaultMessage;

    /**
     * 创建重试错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    RetryErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 非空错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取安全默认消息。
     *
     * @return 非空默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
