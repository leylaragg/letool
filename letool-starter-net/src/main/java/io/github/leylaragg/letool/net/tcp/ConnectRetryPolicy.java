package io.github.leylaragg.letool.net.tcp;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 请求写出前的 TCP 建连重试策略。
 *
 * <p>最大尝试次数包含首次连接。该策略只允许重试尚未写出业务报文的连接过程，
 * 不会重放已经发送的业务请求。</p>
 *
 * @param maxAttempts 最大连接尝试次数，包含首次连接
 * @param initialDelay 首次失败后的基础延迟
 * @param maxDelay 指数退避最大延迟
 * @param jitter 抖动比例，取值范围为 0 到 1
 */
public record ConnectRetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        Duration maxDelay,
        double jitter) {

    /**
     * 校验连接重试策略。
     *
     * @param maxAttempts 最大连接尝试次数，包含首次连接
     * @param initialDelay 首次失败后的基础延迟
     * @param maxDelay 指数退避最大延迟
     * @param jitter 抖动比例，取值范围为 0 到 1
     */
    public ConnectRetryPolicy {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须大于 0");
        }
        requireNonNegative(initialDelay, "initialDelay");
        requireNonNegative(maxDelay, "maxDelay");
        if (maxAttempts > 1
                && (initialDelay.toMillis() <= 0 || maxDelay.toMillis() <= 0)) {
            throw new IllegalArgumentException(
                    "存在重试时 initialDelay 和 maxDelay 必须至少为 1 毫秒");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay 不能小于 initialDelay");
        }
        if (Double.isNaN(jitter) || jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("jitter 必须在 0 到 1 之间");
        }
    }

    /**
     * 创建默认生产连接策略。
     *
     * @return 最多尝试三次、带 20% 抖动的指数退避策略
     */
    public static ConnectRetryPolicy productionDefault() {
        return new ConnectRetryPolicy(
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(2),
                0.2);
    }

    /**
     * 创建禁用连接重试的策略。
     *
     * @return 只执行首次连接的策略
     */
    public static ConnectRetryPolicy noRetry() {
        return new ConnectRetryPolicy(1, Duration.ZERO, Duration.ZERO, 0);
    }

    /**
     * 计算指定失败次数后的下一次连接延迟。
     *
     * @param failedAttempt 已失败的尝试序号，从 1 开始
     * @return 经过最大值约束和随机抖动后的延迟
     */
    public Duration delayAfterFailure(int failedAttempt) {
        if (failedAttempt <= 0) {
            throw new IllegalArgumentException("failedAttempt 必须大于 0");
        }
        if (maxAttempts == 1) {
            return Duration.ZERO;
        }

        long maxMillis = maxDelay.toMillis();
        long delayMillis = initialDelay.toMillis();
        for (int attempt = 1; attempt < failedAttempt && delayMillis < maxMillis; attempt++) {
            delayMillis = Math.min(maxMillis, saturatedDouble(delayMillis));
        }
        if (jitter == 0) {
            return Duration.ofMillis(delayMillis);
        }

        double factor = ThreadLocalRandom.current().nextDouble(1 - jitter, 1 + jitter);
        long jittered = Math.round(delayMillis * factor);
        return Duration.ofMillis(Math.max(0, Math.min(maxMillis, jittered)));
    }

    /**
     * 对正数执行饱和乘二，避免长整型溢出。
     *
     * @param value 当前延迟毫秒数
     * @return 饱和乘二结果
     */
    private long saturatedDouble(long value) {
        return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : value * 2;
    }

    /**
     * 校验时长非负且能够转换为毫秒。
     *
     * @param value 待校验时长
     * @param fieldName 字段名称
     */
    private static void requireNonNegative(Duration value, String fieldName) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " 不能为负数");
        }
        try {
            value.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " 超出毫秒范围", exception);
        }
    }
}
