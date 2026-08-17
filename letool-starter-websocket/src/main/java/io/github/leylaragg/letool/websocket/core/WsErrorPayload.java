package io.github.leylaragg.letool.websocket.core;

/**
 * 允许发送给客户端的安全 WebSocket 错误负载。
 */
public final class WsErrorPayload {

    private final String code;
    private final String message;
    private final String requestMessageId;

    /**
     * 创建客户端错误负载。
     *
     * @param code 稳定错误码
     * @param message 可公开错误消息
     * @param requestMessageId 关联的入站消息 ID，可为 {@code null}
     */
    public WsErrorPayload(String code, String message, String requestMessageId) {
        this.code = code;
        this.message = message;
        this.requestMessageId = requestMessageId;
    }

    /** @return 稳定错误码 */
    public String getCode() {
        return code;
    }

    /** @return 可公开错误消息 */
    public String getMessage() {
        return message;
    }

    /** @return 关联入站消息 ID */
    public String getRequestMessageId() {
        return requestMessageId;
    }
}
