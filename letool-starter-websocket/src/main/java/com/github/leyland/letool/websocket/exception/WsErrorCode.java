package com.github.leyland.letool.websocket.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * WebSocket 模块稳定错误码。
 *
 * <p>错误码用于服务端诊断和客户端错误帧，不承载敏感参数或底层异常信息。</p>
 */
public enum WsErrorCode implements ErrorCode {

    /** 配置项不满足安全约束。 */
    CONFIGURATION_INVALID("WS_001", "WebSocket 配置无效：{0}"),

    /** 握手请求未通过身份认证。 */
    AUTHENTICATION_FAILED("WS_002", "WebSocket 鉴权失败"),

    /** 单个用户的在线会话数量超过限制。 */
    SESSION_LIMIT_EXCEEDED("WS_003", "用户 {0} 的 WebSocket 会话数已达到上限"),

    /** 入站消息格式或内容不合法。 */
    INVALID_MESSAGE("WS_004", "WebSocket 消息无效：{0}"),

    /** 多个处理器声明了相同消息类型。 */
    ROUTE_CONFLICT("WS_005", "WebSocket 消息路由冲突：{0}"),

    /** 当前主体无权处理指定消息。 */
    ACCESS_DENIED("WS_006", "无权处理 WebSocket 消息：{0}"),

    /** 消息无法投递到目标连接。 */
    DELIVERY_FAILED("WS_007", "WebSocket 消息发送失败：{0}"),

    /** 业务消息处理器执行失败。 */
    HANDLER_FAILED("WS_008", "WebSocket 消息处理失败：{0}");

    private final String code;
    private final String defaultMessage;

    /**
     * 创建 WebSocket 错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认消息模板
     */
    WsErrorCode(String code, String defaultMessage) {
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
     * 获取默认消息模板。
     *
     * @return 默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
