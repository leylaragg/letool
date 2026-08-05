package com.github.leyland.letool.websocket.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * WebSocket 模块统一系统异常。
 *
 * <p>异常始终携带 {@link WsErrorCode}，并通过语义化工厂方法限制调用方随意创建
 * 不稳定错误码。底层技术异常会保留为原因链，但不会直接写入客户端错误帧。</p>
 */
public final class WsException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建 WebSocket 系统异常。
     *
     * @param errorCode WebSocket 错误码
     * @param messageArgs 消息模板参数
     * @param cause 底层原因，无法取得时可为 {@code null}
     */
    private WsException(WsErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建配置错误异常。
     *
     * @param reason 安全的错误原因
     * @return 配置错误异常
     */
    public static WsException configurationInvalid(String reason) {
        return new WsException(WsErrorCode.CONFIGURATION_INVALID, new Object[]{reason}, null);
    }

    /**
     * 创建鉴权失败异常。
     *
     * @return 鉴权失败异常
     */
    public static WsException authenticationFailed() {
        return new WsException(WsErrorCode.AUTHENTICATION_FAILED, null, null);
    }

    /**
     * 创建会话数量超限异常。
     *
     * @param userId 用户标识
     * @return 会话数量超限异常
     */
    public static WsException sessionLimitExceeded(String userId) {
        return new WsException(WsErrorCode.SESSION_LIMIT_EXCEEDED, new Object[]{userId}, null);
    }

    /**
     * 创建非法消息异常。
     *
     * @param reason 安全的错误原因
     * @return 非法消息异常
     */
    public static WsException invalidMessage(String reason) {
        return new WsException(WsErrorCode.INVALID_MESSAGE, new Object[]{reason}, null);
    }

    /**
     * 创建路由冲突异常。
     *
     * @param messageType 冲突的消息类型
     * @return 路由冲突异常
     */
    public static WsException routeConflict(String messageType) {
        return new WsException(WsErrorCode.ROUTE_CONFLICT, new Object[]{messageType}, null);
    }

    /**
     * 创建访问拒绝异常。
     *
     * @param messageType 被拒绝的消息类型
     * @return 访问拒绝异常
     */
    public static WsException accessDenied(String messageType) {
        return new WsException(WsErrorCode.ACCESS_DENIED, new Object[]{messageType}, null);
    }

    /**
     * 创建消息投递异常。
     *
     * @param target 目标会话或用户
     * @param cause 底层发送异常
     * @return 消息投递异常
     */
    public static WsException deliveryFailed(String target, Throwable cause) {
        return new WsException(WsErrorCode.DELIVERY_FAILED, new Object[]{target}, cause);
    }

    /**
     * 创建处理器执行异常。
     *
     * @param messageType 消息类型
     * @param cause 业务处理器异常
     * @return 处理器执行异常
     */
    public static WsException handlerFailed(String messageType, Throwable cause) {
        return new WsException(WsErrorCode.HANDLER_FAILED, new Object[]{messageType}, cause);
    }
}
