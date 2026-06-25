package com.github.leyland.letool.tool.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * 重试工具——支持固定间隔重试、指数退避重试、按返回结果重试.
 *
 * <h3>三种重试策略</h3>
 * <table>
 *   <tr><th>方法</th><th>策略</th><th>适用场景</th></tr>
 *   <tr><td>{@link #retry(Callable, int, long)}</td><td>固定间隔</td><td>临时故障（如网络抖动）</td></tr>
 *   <tr><td>{@link #retryExponential(Callable, int, long)}</td><td>指数退避</td><td>服务过载、限流恢复</td></tr>
 *   <tr><td>{@link #retryByResult(Callable, int, long, Predicate)}</td><td>按结果判断</td><td>异步结果轮询、状态等待</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 简单重试：最多 3 次，每次间隔 500ms
 * String result = RetryUtil.retry(() -> httpClient.get(url), 3, 500);
 *
 * // 指数退避：基础 100ms，每次翻倍（100, 200, 400, 800...）
 * String result = RetryUtil.retryExponential(() -> db.query(sql), 5, 100);
 *
 * // 按返回值判断是否重试（如轮询异步任务状态）
 * JobStatus status = RetryUtil.retryByResult(
 *     () -> jobService.getStatus(jobId),
 *     10, 2000,
 *     s -> s == JobStatus.RUNNING   // 仍在运行中 → 继续等待
 * );
 *
 * // 按异常类型判断是否重试（只重试连接异常，不重试业务异常）
 * String result = RetryUtil.retry(
 *     () -> api.call(),
 *     3, 1000,
 *     e -> e instanceof java.net.ConnectException
 * );
 * }</pre>
 */
public final class RetryUtil {

    private static final Logger log = LoggerFactory.getLogger(RetryUtil.class);

    private RetryUtil() {}

    // ======================== 固定间隔重试 ========================

    /**
     * 固定间隔重试——所有异常均触发重试.
     *
     * @param task       待执行任务
     * @param maxRetries 最大重试次数（总执行次数 = maxRetries + 1）
     * @param intervalMs 重试间隔（毫秒）
     * @param <T>        返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果重试耗尽后仍然失败（包裹最后一次异常）
     */
    public static <T> T retry(Callable<T> task, int maxRetries, long intervalMs) {
        return retry(task, maxRetries, intervalMs, e -> true);
    }

    /**
     * 固定间隔重试——仅指定异常类型触发重试.
     *
     * @param task       待执行任务
     * @param maxRetries 最大重试次数
     * @param intervalMs 重试间隔（毫秒）
     * @param retryOn    异常过滤器（返回 {@code true} 才重试）
     * @param <T>        返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果重试耗尽后仍然失败
     */
    public static <T> T retry(Callable<T> task, int maxRetries, long intervalMs,
                              Predicate<Exception> retryOn) {
        Exception last = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return task.call();
            } catch (Exception e) {
                last = e;
                if (i < maxRetries && retryOn.test(e)) {
                    log.debug("Retry {}/{} after {}ms: {}", i + 1, maxRetries, intervalMs, e.getMessage());
                    sleep(intervalMs);
                } else {
                    break;
                }
            }
        }
        throw new RuntimeException("Failed after " + maxRetries + " retries", last);
    }

    // ======================== 指数退避重试 ========================

    /**
     * 指数退避重试——间隔时间按倍数递增（baseMs × multiplier⁰, baseMs × multiplier¹, ...）.
     *
     * <p>默认 multiplier = 2.0，即 100ms → 200ms → 400ms → 800ms...</p>
     *
     * @param task       待执行任务
     * @param maxRetries 最大重试次数
     * @param baseMs     基础间隔（毫秒）
     * @param <T>        返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果重试耗尽后仍然失败
     */
    public static <T> T retryExponential(Callable<T> task, int maxRetries, long baseMs) {
        return retryExponential(task, maxRetries, baseMs, 2.0);
    }

    /**
     * 指数退避重试——自定义倍数.
     *
     * @param task       待执行任务
     * @param maxRetries 最大重试次数
     * @param baseMs     基础间隔（毫秒）
     * @param multiplier 退避倍数（通常 1.5 ~ 2.0）
     * @param <T>        返回值类型
     * @return 任务执行结果
     * @throws RuntimeException 如果重试耗尽后仍然失败
     */
    public static <T> T retryExponential(Callable<T> task, int maxRetries, long baseMs, double multiplier) {
        Exception last = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return task.call();
            } catch (Exception e) {
                last = e;
                if (i < maxRetries) {
                    long delay = (long) (baseMs * Math.pow(multiplier, i));
                    log.debug("Retry {}/{} after {}ms (exponential): {}", i + 1, maxRetries, delay, e.getMessage());
                    sleep(delay);
                }
            }
        }
        throw new RuntimeException("Failed after " + maxRetries + " exponential retries", last);
    }

    // ======================== 按结果重试 ========================

    /**
     * 按返回结果判断是否需要重试.
     *
     * <p>适用场景：轮询异步任务状态，直到任务完成或不再匹配重试条件.</p>
     *
     * @param task        待执行任务
     * @param maxRetries  最大重试次数
     * @param intervalMs  重试间隔（毫秒）
     * @param shouldRetry 返回值判断器（返回 {@code true} 则继续重试）
     * @param <T>         返回值类型
     * @return 任务执行结果（最后一次执行的结果）
     * @throws RuntimeException 如果重试耗尽后仍然需要重试
     */
    public static <T> T retryByResult(Callable<T> task, int maxRetries, long intervalMs,
                                      Predicate<T> shouldRetry) {
        Exception last = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                T result = task.call();
                if (i < maxRetries && shouldRetry.test(result)) {
                    log.debug("Retry {}/{} due to result: {}", i + 1, maxRetries, result);
                    sleep(intervalMs);
                } else {
                    return result;
                }
            } catch (Exception e) {
                last = e;
                if (i < maxRetries) {
                    sleep(intervalMs);
                }
            }
        }
        if (last != null) throw new RuntimeException("Failed after " + maxRetries + " retries", last);
        throw new RuntimeException("Failed after " + maxRetries + " retries with no exception");
    }

    /**
     * 线程休眠，捕获并恢复中断状态.
     *
     * @param ms 休眠毫秒数
     */
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
