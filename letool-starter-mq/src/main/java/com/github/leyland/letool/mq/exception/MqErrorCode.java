package com.github.leyland.letool.mq.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * MQ 模块稳定错误码。
 *
 * @author leyland
 * @since 2.0.0
 */
public enum MqErrorCode implements ErrorCode {

    /** MQ 配置不合法。 */
    CONFIGURATION_INVALID("MQ_001", "MQ 配置不合法：{0}"),

    /** 未找到指定 MQ Provider。 */
    PROVIDER_NOT_FOUND("MQ_002", "未找到 MQ Provider：{0}"),

    /** MQ Provider 名称重复。 */
    DUPLICATE_PROVIDER("MQ_003", "MQ Provider 名称重复：{0}"),

    /** MQ 消息或发送请求不合法。 */
    MESSAGE_INVALID("MQ_004", "MQ 消息不合法：{0}"),

    /** MQ 发送过程出现底层异常。 */
    SEND_FAILED("MQ_005", "MQ Provider {0} 发送失败，当前结果未知"),

    /** MQ 发送通道拒绝消息。 */
    SEND_REJECTED("MQ_006", "MQ Provider {0} 的发送通道拒绝消息：{1}"),

    /** MQ Header 不合法。 */
    HEADER_INVALID("MQ_007", "MQ Header 不合法：{0}"),

    /** 用户扩展 Provider 执行失败。 */
    PROVIDER_EXECUTION_FAILED("MQ_008", "MQ Provider {0} 执行失败，当前结果未知");

    private final String code;
    private final String defaultMessage;

    /**
     * 创建 MQ 错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认中文消息模板
     */
    MqErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 稳定错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认中文消息模板。
     *
     * @return 默认中文消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
