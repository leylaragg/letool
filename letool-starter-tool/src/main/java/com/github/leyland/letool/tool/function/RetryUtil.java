package com.github.leyland.letool.tool.function;

import io.github.resilience4j.retry.MaxRetriesExceededException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * 基于 Resilience4j Retry 的同步重试便捷工具。
 *
 * <p>Letool 负责参数契约、统一异常和线程中断语义，实际重试循环与等待由成熟框架执行。
 * 工具不会记录任务返回值、异常消息或业务参数。</p>
 */
public final class RetryUtil {

    /** Resilience4j 内部实例名称，不包含业务动态信息。 */
    private static final String RETRY_NAME = "letool-retry";

    /** 工具类不允许实例化。 */
    private RetryUtil() {
    }

    /**
     * 使用指定策略同步执行可返回结果的任务。
     *
     * @param task 待执行任务
     * @param policy 不可变重试策略
     * @param <T> 任务返回值类型
     * @return 首个不再满足重试条件的任务结果
     * @throws RetryOperationException 参数无效、任务失败、次数耗尽或线程中断时抛出
     */
    public static <T> T execute(Callable<T> task, RetryPolicy<T> policy) {
        if (task == null) {
            throw RetryOperationException.invalidArgument("task");
        }
        if (policy == null) {
            throw RetryOperationException.invalidArgument("policy");
        }

        AtomicInteger attempts = new AtomicInteger();
        AtomicBoolean lastFailureRetryable = new AtomicBoolean();
        AtomicBoolean resultPredicateFailed = new AtomicBoolean();
        RetryConfig retryConfig = RetryConfig.<T>custom()
                .maxAttempts(policy.maxAttempts())
                .intervalFunction(policy.intervalFunction())
                .retryOnException(throwable -> {
                    lastFailureRetryable.set(false);
                    // 结果判断器属于策略代码，其自身失败时不得重复执行业务任务。
                    if (resultPredicateFailed.getAndSet(false)) {
                        return false;
                    }
                    boolean retryable = !isInterruption(throwable)
                            && policy.retryOnException().test(throwable);
                    lastFailureRetryable.set(retryable);
                    return retryable;
                })
                .retryOnResult(result -> {
                    resultPredicateFailed.set(false);
                    try {
                        return policy.retryOnResult().test(result);
                    } catch (RuntimeException exception) {
                        resultPredicateFailed.set(true);
                        throw exception;
                    }
                })
                .failAfterMaxAttempts(true)
                .build();
        Retry retry = Retry.of(RETRY_NAME, retryConfig);

        try {
            return retry.executeCallable(() -> {
                attempts.incrementAndGet();
                return task.call();
            });
        } catch (MaxRetriesExceededException exception) {
            throw RetryOperationException.exhausted(attempts.get(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw RetryOperationException.interrupted(attempts.get(), exception);
        } catch (Exception exception) {
            if (Thread.currentThread().isInterrupted() || isInterruption(exception)) {
                Thread.currentThread().interrupt();
                throw RetryOperationException.interrupted(attempts.get(), exception);
            }
            if (attempts.get() >= policy.maxAttempts() && lastFailureRetryable.get()) {
                throw RetryOperationException.exhausted(attempts.get(), exception);
            }
            throw RetryOperationException.executionFailed(attempts.get(), exception);
        }
    }

    /**
     * 使用固定等待时间和默认异常条件执行重试。
     *
     * <p>默认情况下，除线程中断和显式取消外，其他异常都会触发重试。</p>
     *
     * @param task 待执行任务
     * @param maxAttempts 包含首次调用在内的最大尝试次数
     * @param delay 每次重试前的固定等待时间
     * @param <T> 任务返回值类型
     * @return 首个成功执行的任务结果
     * @throws RetryOperationException 参数无效、任务失败、次数耗尽或线程中断时抛出
     */
    public static <T> T retry(
            Callable<T> task,
            int maxAttempts,
            Duration delay) {
        return retry(task, maxAttempts, delay, throwable -> true);
    }

    /**
     * 使用固定等待时间和指定异常条件执行重试。
     *
     * @param task 待执行任务
     * @param maxAttempts 包含首次调用在内的最大尝试次数
     * @param delay 每次重试前的固定等待时间
     * @param retryOnException 异常重试条件，返回 {@code true} 时允许重试
     * @param <T> 任务返回值类型
     * @return 首个成功执行的任务结果
     * @throws RetryOperationException 参数无效、任务失败、次数耗尽或线程中断时抛出
     */
    public static <T> T retry(
            Callable<T> task,
            int maxAttempts,
            Duration delay,
            Predicate<Throwable> retryOnException) {
        RetryPolicy<T> policy = RetryPolicy.<T>builder()
                .maxAttempts(maxAttempts)
                .fixedDelay(delay)
                .retryOnException(retryOnException)
                .build();
        return execute(task, policy);
    }

    /**
     * 使用带最大等待上限的指数退避策略执行重试。
     *
     * <p>默认情况下，除线程中断和显式取消外，其他异常都会触发重试。</p>
     *
     * @param task 待执行任务
     * @param maxAttempts 包含首次调用在内的最大尝试次数
     * @param initialDelay 首次重试前的正等待时间
     * @param multiplier 每次递增倍数，必须为大于一的有限值
     * @param maxDelay 单次等待上限，不得小于初始等待时间
     * @param <T> 任务返回值类型
     * @return 首个成功执行的任务结果
     * @throws RetryOperationException 参数无效、任务失败、次数耗尽或线程中断时抛出
     */
    public static <T> T retryExponential(
            Callable<T> task,
            int maxAttempts,
            Duration initialDelay,
            double multiplier,
            Duration maxDelay) {
        RetryPolicy<T> policy = RetryPolicy.<T>builder()
                .maxAttempts(maxAttempts)
                .exponentialBackoff(initialDelay, multiplier, maxDelay)
                .build();
        return execute(task, policy);
    }

    /**
     * 使用固定等待时间按任务结果执行重试。
     *
     * <p>结果条件返回 {@code true} 表示当前结果尚不可接受，需要继续执行任务。</p>
     *
     * @param task 待执行任务
     * @param maxAttempts 包含首次调用在内的最大尝试次数
     * @param delay 每次重试前的固定等待时间
     * @param retryOnResult 结果重试条件，返回 {@code true} 时允许重试
     * @param <T> 任务返回值类型
     * @return 首个不再满足重试条件的任务结果
     * @throws RetryOperationException 参数无效、任务失败、次数耗尽或线程中断时抛出
     */
    public static <T> T retryByResult(
            Callable<T> task,
            int maxAttempts,
            Duration delay,
            Predicate<T> retryOnResult) {
        RetryPolicy<T> policy = RetryPolicy.<T>builder()
                .maxAttempts(maxAttempts)
                .fixedDelay(delay)
                .retryOnResult(retryOnResult)
                .build();
        return execute(task, policy);
    }

    /**
     * 判断异常原因链是否表示线程中断或显式取消。
     *
     * <p>原因链遍历设置深度上限，避免第三方异常构造循环原因链后导致工具无法返回。</p>
     *
     * @param throwable 待检查异常
     * @return 原因链包含中断或取消异常时返回 {@code true}
     */
    private static boolean isInterruption(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (current instanceof InterruptedException
                    || current instanceof CancellationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
