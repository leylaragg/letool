package com.github.leyland.letool.mq.exception;

/**
 * MQ 消息队列异常 —— 所有 MQ 模块操作异常的基类.
 *
 * <p>继承 {@link RuntimeException}，适用于消息发送失败、序列化错误、订阅异常等场景，
 * 无需强制 try-catch，由全局异常处理器统一拦截.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 直接抛出
 * throw new MqException("消息发送失败: topic=" + topic);
 *
 * // 保留原始异常链
 * throw new MqException("连接 RabbitMQ 失败", connectionException);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class MqException extends RuntimeException {

    /**
     * 创建带错误描述的 MQ 异常.
     *
     * @param message 错误描述（面向开发者，包含上下文信息如 topic / messageId）
     */
    public MqException(String message) {
        super(message);
    }

    /**
     * 创建带错误描述和原始异常的 MQ 异常.
     *
     * @param message 错误描述
     * @param cause   原始异常（保留完整堆栈信息，用于排查根因）
     */
    public MqException(String message, Throwable cause) {
        super(message, cause);
    }
}
