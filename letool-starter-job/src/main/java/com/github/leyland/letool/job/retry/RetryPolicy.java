package com.github.leyland.letool.job.retry;

/**
 * 任务重试策略——提供重试判断和指数退避延迟计算.
 *
 * <p>该工具类用于判断是否需要继续重试以及计算每次重试的等待时间.
 * 采用指数退避算法，每次重试的延迟 = {@code baseMs * (multiplier ^ retryCount)}.</p>
 *
 * <h3>退避策略说明</h3>
 * <p>假设 {@code baseMs=1000, multiplier=2.0}：</p>
 * <ul>
 *   <li>第1次重试：等待 1000ms</li>
 *   <li>第2次重试：等待 2000ms</li>
 *   <li>第3次重试：等待 4000ms</li>
 *   <li>第4次重试：等待 8000ms</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * int maxRetries = 3;
 * long baseMs = 1000;
 * double multiplier = 2.0;
 *
 * for (int retry = 0; retry <= maxRetries; retry++) {
 *     try {
 *         executeTask();
 *         break;
 *     } catch (Exception e) {
 *         if (!RetryPolicy.shouldRetry(retry, maxRetries)) {
 *             throw e;
 *         }
 *         long delay = RetryPolicy.getBackoffDelay(retry, baseMs, multiplier);
 *         Thread.sleep(delay);
 *     }
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.job.core.JobScheduler
 */
public final class RetryPolicy {

    // ======================== 私有构造 ========================

    private RetryPolicy() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ======================== 重试判断 ========================

    /**
     * 判断当前重试是否应该继续.
     *
     * <p>当 {@code currentRetry < maxRetries} 时返回 {@code true}，
     * 表示还可以继续重试. 注意 currentRetry 从0开始计数（0表示第一次执行/重试前）.</p>
     *
     * @param currentRetry 当前重试次数（从0开始，0表示首次执行失败后的第一次重试）
     * @param maxRetries   最大重试次数
     * @return {@code true} 如果应该继续重试
     */
    public static boolean shouldRetry(int currentRetry, int maxRetries) {
        return currentRetry < maxRetries;
    }

    // ======================== 退避延迟计算 ========================

    /**
     * 计算指数退避延迟时间.
     *
     * <p>公式：{@code baseMs * (multiplier ^ retryCount)}. 结果上限为60秒，
     * 防止退避时间无限增长.</p>
     *
     * @param retryCount 当前重试次数（从0开始）
     * @param baseMs     基础退避时间（毫秒）
     * @param multiplier 退避倍率（如2.0表示指数退避）
     * @return 需要等待的毫秒数（上限60秒）
     */
    public static long getBackoffDelay(int retryCount, long baseMs, double multiplier) {
        if (retryCount < 0 || baseMs <= 0 || multiplier <= 0) {
            return 0;
        }
        double delay = baseMs * Math.pow(multiplier, retryCount);
        long result = Math.round(delay);
        // 上限60秒，防止无限等待
        return Math.min(result, 60_000L);
    }
}
