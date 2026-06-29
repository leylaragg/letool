package com.github.leyland.letool.websocket.exception;

/**
 * WebSocket 模块统一异常类，所有 WebSocket 相关的运行时异常均使用此类或其子类包装。
 *
 * <p>设计目标：</p>
 * <ul>
 *   <li>继承 {@link RuntimeException}，无需在方法签名中声明 {@code throws}</li>
 *   <li>提供 {@code errorCode} 机制，便于全局异常处理器或消息处理器按错误码分流</li>
 *   <li>保留原始异常链，方便问题排查</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 简单抛出
 * throw new WsException("WS_001", "会话不存在");
 *
 * // 带原始异常
 * throw new WsException("WS_002", "消息发送失败", ioException);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsException extends RuntimeException {

    /** 错误码，全局唯一 */
    private final String errorCode;

    // ======================== 构造 ========================

    /**
     * 创建指定错误码和描述的异常。
     *
     * @param errorCode 错误码（全局唯一，如 "WS_001"）
     * @param message   错误描述
     */
    public WsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建指定错误码、描述和原始异常的异常。
     *
     * @param errorCode 错误码
     * @param message   错误描述
     * @param cause     原始异常（保留完整堆栈信息）
     */
    public WsException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    // ======================== Getter ========================

    /**
     * 获取错误码。
     *
     * @return 创建时指定的错误码
     */
    public String getErrorCode() {
        return errorCode;
    }
}
