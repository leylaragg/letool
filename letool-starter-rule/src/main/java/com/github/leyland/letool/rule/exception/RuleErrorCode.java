package com.github.leyland.letool.rule.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 规则模块稳定错误码。
 *
 * <p>错误码供程序判断和外部错误协议使用，默认消息可在没有国际化资源时
 * 提供稳定的日志与响应兜底文案。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum RuleErrorCode implements ErrorCode {

    /** 规则链标识为空或仅包含空白字符。 */
    CHAIN_ID_INVALID("RULE_001", "规则链标识不能为空"),

    /** LiteFlow 规则链执行失败。 */
    EXECUTION_FAILED("RULE_002", "规则链执行失败：{0}");

    /** 用于诊断和外部错误协议的稳定错误码。 */
    private final String code;

    /** 找不到国际化消息时使用的默认消息模板。 */
    private final String defaultMessage;

    /**
     * 创建规则模块错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认消息模板
     */
    RuleErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取规则模块稳定错误码。
     *
     * @return 非空白错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取规则模块默认消息模板。
     *
     * @return 非空白默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
