package com.github.leyland.letool.mail.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 邮件模块对外暴露的稳定错误码。
 */
public enum MailErrorCode implements ErrorCode {

    /** 邮件账户或传输参数配置不合法。 */
    CONFIGURATION_INVALID("MAIL_001", "邮件配置不合法：{0}"),

    /** 邮件请求缺少必填字段或包含无效数据。 */
    REQUEST_INVALID("MAIL_002", "邮件请求不合法：{0}"),

    /** 邮件构造、连接或投递失败。 */
    DELIVERY_FAILED("MAIL_003", "邮件投递失败"),

    /** 异步执行器已经关闭或拒绝任务。 */
    ASYNC_UNAVAILABLE("MAIL_004", "邮件异步执行器不可用");

    /** 供程序判断和响应映射使用的稳定错误码。 */
    private final String code;

    /** 未配置国际化文案时使用的默认消息。 */
    private final String defaultMessage;

    /**
     * 创建邮件错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    MailErrorCode(String code, String defaultMessage) {
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
     * 获取默认错误消息模板。
     *
     * @return 非空默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
