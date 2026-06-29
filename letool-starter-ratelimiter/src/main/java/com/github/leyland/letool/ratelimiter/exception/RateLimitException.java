package com.github.leyland.letool.ratelimiter.exception;

/**
 * 限流模块的统一异常类。
 *
 * <p>用于表示限流和熔断操作中出现的各类异常，包括但不限于：</p>
 * <ul>
 *   <li><b>请求被限流拒绝</b>：在指定时间内无法获取到许可</li>
 *   <li><b>熔断器已打开</b>：熔断器处于 OPEN 状态，请求被快速失败</li>
 *   <li><b>配置错误</b>：注解参数配置不合理</li>
 * </ul>
 *
 * <p>继承自 {@link RuntimeException}，属于非受检异常，调用方可根据需要选择捕获。</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.ratelimiter.aspect.RateLimitAspect
 * @see com.github.leyland.letool.ratelimiter.core.RateLimitTemplate
 */
public class RateLimitException extends RuntimeException {

    // ======================== 构造方法 ========================

    /**
     * 使用错误消息构造异常。
     *
     * @param message 描述错误详情的消息
     */
    public RateLimitException(String message) {
        super(message);
    }

    /**
     * 使用错误消息和根因构造异常。
     *
     * @param message 描述错误详情的消息
     * @param cause   导致此异常的原始异常
     */
    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
