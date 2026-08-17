package io.github.leylaragg.letool.ratelimiter.exception;

import io.github.leylaragg.letool.exception.core.BusinessException;

import java.io.Serial;

/**
 * 请求被限流拒绝时抛出的统一业务异常。
 *
 * <p>该异常表示系统按预期执行了流量保护，调用方可以将其映射为 HTTP 429、
 * 友好提示或其他业务降级响应。</p>
 */
public final class RateLimitException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建限流业务异常。
     *
     * @param policy 策略名称
     */
    private RateLimitException(String policy) {
        super(RateLimitErrorCode.REQUEST_REJECTED, new Object[]{policy}, null, null);
    }

    /**
     * 创建请求拒绝异常。
     *
     * @param policy 限流策略名称
     * @return 限流业务异常
     */
    public static RateLimitException rejected(String policy) {
        return new RateLimitException(requireValue(policy, "policy"));
    }

    /**
     * 校验异常消息参数。
     *
     * @param value     参数值
     * @param fieldName 参数名称
     * @return 已校验参数
     */
    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
