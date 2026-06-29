package com.github.leyland.letool.monitor.exception;

/**
 * 监控模块统一异常，用于封装监控采集、统计、告警过程中的运行时错误.
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>指标采集失败时抛出</li>
 *   <li>告警通知发送异常时抛出</li>
 *   <li>数据清理任务执行失败时抛出</li>
 *   <li>配置参数不合法时抛出</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * throw new MonitorException("指标采集失败: 无法获取 JVM 堆内存使用量");
 * throw new MonitorException("钉钉告警发送失败", cause);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class MonitorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用错误消息构造监控异常.
     *
     * @param message 错误描述信息
     */
    public MonitorException(String message) {
        super(message);
    }

    /**
     * 使用错误消息和根因构造监控异常.
     *
     * @param message 错误描述信息
     * @param cause   原始异常
     */
    public MonitorException(String message, Throwable cause) {
        super(message, cause);
    }
}
