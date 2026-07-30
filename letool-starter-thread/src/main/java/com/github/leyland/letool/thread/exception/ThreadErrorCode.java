package com.github.leyland.letool.thread.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 线程模块对外暴露的稳定错误码。
 */
public enum ThreadErrorCode implements ErrorCode {

    /** 线程池名称、容量或生命周期参数不合法。 */
    CONFIGURATION_INVALID("THREAD_001", "线程池配置不合法：{0}"),

    /** 显式创建线程池时发现同名实例已经存在。 */
    POOL_ALREADY_EXISTS("THREAD_002", "线程池已存在"),

    /** 调整或查询的线程池尚未注册。 */
    POOL_NOT_FOUND("THREAD_003", "线程池不存在");

    /** 供程序判断和响应映射使用的稳定错误码。 */
    private final String code;

    /** 未配置国际化文案时使用的默认消息。 */
    private final String defaultMessage;

    /**
     * 创建线程模块错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    ThreadErrorCode(String code, String defaultMessage) {
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
