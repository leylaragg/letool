package com.github.leyland.letool.net.exception;

/**
 * 网络通信框架统一异常 —— 所有网络相关异常的根类型.
 *
 * <p>携带可选的路由 ID，方便定位故障来源路由。支持链式异常传递.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * throw new NetException("TCP connection refused", e, "icbc-pay");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class NetException extends RuntimeException {

    /**
     * 发生异常的路由 ID（可选）.
     */
    private final String routeId;

    // ======================== 构造器 ========================

    /**
     * 仅携带错误消息.
     *
     * @param message 错误描述
     */
    public NetException(String message) {
        super(message);
        this.routeId = null;
    }

    /**
     * 携带错误消息和原始异常.
     *
     * @param message 错误描述
     * @param cause   原始异常
     */
    public NetException(String message, Throwable cause) {
        super(message, cause);
        this.routeId = null;
    }

    /**
     * 携带错误消息、原始异常和路由 ID.
     *
     * @param message 错误描述
     * @param cause   原始异常
     * @param routeId 发生异常的路由标识
     */
    public NetException(String message, Throwable cause, String routeId) {
        super(message, cause);
        this.routeId = routeId;
    }

    /**
     * 携带错误消息和路由 ID.
     *
     * @param message 错误描述
     * @param routeId 发生异常的路由标识
     */
    public NetException(String message, String routeId) {
        super(message);
        this.routeId = routeId;
    }

    // ======================== Getter ========================

    /**
     * 获取发生异常的路由 ID.
     *
     * @return 路由 ID，可能为 {@code null}
     */
    public String getRouteId() {
        return routeId;
    }
}
