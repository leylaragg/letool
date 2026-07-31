package com.github.leyland.letool.net.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * 网络模块稳定错误码。
 */
public enum NetErrorCode implements ErrorCode {

    /** 网络客户端配置不合法。 */
    CONFIGURATION_INVALID("NET_CONFIG_INVALID", "网络客户端配置不合法：{0}"),

    /** 网络运行时已经关闭。 */
    RUNTIME_CLOSED("NET_RUNTIME_CLOSED", "网络运行时已经关闭"),

    /** TCP 连接建立失败。 */
    CONNECT_FAILED("NET_TCP_CONNECT_FAILED", "连接 TCP 服务失败：{0}:{1}"),

    /** 从连接池获取连接超时。 */
    ACQUIRE_TIMEOUT("NET_TCP_ACQUIRE_TIMEOUT", "在限定时间内未获取到 TCP 连接"),

    /** 请求数量超过有界容量。 */
    REQUEST_OVERLOADED("NET_TCP_REQUEST_OVERLOADED", "TCP 客户端请求数量超过容量上限"),

    /** 请求载荷编码失败。 */
    ENCODE_FAILED("NET_TCP_ENCODE_FAILED", "TCP 请求载荷编码失败"),

    /** 响应载荷解码失败。 */
    DECODE_FAILED("NET_TCP_DECODE_FAILED", "TCP 响应载荷解码失败"),

    /** 业务报文超过配置上限。 */
    FRAME_TOO_LARGE("NET_TCP_FRAME_TOO_LARGE", "TCP 业务报文超过最大长度：{0} 字节"),

    /** 写入网络通道失败。 */
    WRITE_FAILED("NET_TCP_WRITE_FAILED", "TCP 请求写入失败"),

    /** 心跳载荷创建或写入失败。 */
    HEARTBEAT_FAILED("NET_TCP_HEARTBEAT_FAILED", "TCP 心跳创建或写入失败"),

    /** 连续心跳应答超时。 */
    HEARTBEAT_TIMEOUT(
            "NET_TCP_HEARTBEAT_TIMEOUT",
            "TCP 心跳连续 {0} 个应答窗口未收到 ACK"),

    /** 完整请求响应超时。 */
    REQUEST_TIMEOUT("NET_TCP_REQUEST_TIMEOUT", "TCP 请求在 {0} 毫秒内未收到完整响应"),

    /** 等待响应期间通道被关闭。 */
    CHANNEL_CLOSED("NET_TCP_CHANNEL_CLOSED", "TCP 通道在响应完成前关闭"),

    /** 客户端已经关闭。 */
    CLIENT_CLOSED("NET_TCP_CLIENT_CLOSED", "TCP 客户端已经关闭"),

    /** 禁止在 Netty 事件线程执行阻塞调用。 */
    BLOCKING_ON_EVENT_LOOP(
            "NET_TCP_BLOCKING_ON_EVENT_LOOP",
            "禁止在 Netty EventLoop 线程中执行阻塞请求"),

    /** 客户端内部状态不一致。 */
    INTERNAL_STATE_ERROR("NET_TCP_INTERNAL_STATE_ERROR", "TCP 客户端内部状态不一致");

    /** 稳定的外部错误码。 */
    private final String code;

    /** 不依赖 Spring 上下文的默认中文消息。 */
    private final String defaultMessage;

    /**
     * 创建网络错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认消息模板
     */
    NetErrorCode(String code, String defaultMessage) {
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
     * 获取默认消息模板。
     *
     * @return 默认中文消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
