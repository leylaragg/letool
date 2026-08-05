package com.github.leyland.letool.job.retry;

/**
 * 提供无副作用的任务重试判断和饱和指数退避计算。
 */
public final class RetryPolicy {

    private RetryPolicy() {
    }

    /**
     * 判断当前失败后是否仍可安排一次额外重试。
     *
     * @param currentRetry 当前已经进入的重试次数，首次执行为零
     * @param maxRetries 最大额外重试次数
     * @return 尚未达到上限时返回 {@code true}
     */
    public static boolean shouldRetry(int currentRetry, int maxRetries) {
        return currentRetry >= 0 && maxRetries >= 0 && currentRetry < maxRetries;
    }

    /**
     * 计算带显式上限的指数退避延迟。
     *
     * @param retryCount 当前重试次数，首次失败为零
     * @param baseMs 第一次重试基础延迟毫秒数
     * @param multiplier 退避倍率
     * @param maxBackoffMs 单次延迟最大毫秒数
     * @return 不超过上限的退避延迟
     * @throws IllegalArgumentException 参数不满足非负或有限正数约束时抛出
     */
    public static long getBackoffDelay(
            int retryCount,
            long baseMs,
            double multiplier,
            long maxBackoffMs) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount 不能小于 0");
        }
        if (baseMs < 0) {
            throw new IllegalArgumentException("baseMs 不能小于 0");
        }
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            throw new IllegalArgumentException("multiplier 必须为有限正数");
        }
        if (maxBackoffMs <= 0) {
            throw new IllegalArgumentException("maxBackoffMs 必须大于 0");
        }
        if (baseMs == 0) {
            return 0;
        }
        double calculated = baseMs * Math.pow(multiplier, retryCount);
        if (!Double.isFinite(calculated) || calculated >= maxBackoffMs) {
            return maxBackoffMs;
        }
        return Math.min(Math.round(calculated), maxBackoffMs);
    }
}
