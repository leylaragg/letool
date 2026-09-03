package io.github.leylaragg.letool.tool.function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RetryUtil} 的尝试次数、失败分类、结果重试和中断契约测试。
 */
class RetryUtilTest {

    /**
     * 清理测试线程的中断标记，避免中断场景污染后续用例。
     */
    @AfterEach
    void clearInterruptedFlag() {
        Thread.interrupted();
    }

    /**
     * 验证可重试异常在最大尝试次数内恢复时返回最终结果。
     */
    @Test
    void shouldRetryRetryableExceptionUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy<String> policy = ioRetryPolicy(Duration.ZERO);

        String result = RetryUtil.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException("temporary failure");
            }
            return "success";
        }, policy);

        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    /**
     * 验证最大尝试次数小于一时返回稳定参数错误码。
     */
    @Test
    void shouldRejectInvalidMaxAttempts() {
        RetryOperationException exception = assertThrows(
                RetryOperationException.class,
                () -> RetryPolicy.builder().maxAttempts(0).build()
        );
        RetryOperationException multiplierException = assertThrows(
                RetryOperationException.class,
                () -> RetryPolicy.builder()
                        .exponentialBackoff(
                                Duration.ofMillis(10),
                                1.0,
                                Duration.ofSeconds(1))
                        .build()
        );
        RetryOperationException jitterException = assertThrows(
                RetryOperationException.class,
                () -> RetryPolicy.builder()
                        .exponentialRandomBackoff(
                                Duration.ofMillis(10),
                                2.0,
                                1.1,
                                Duration.ofSeconds(1))
                        .build()
        );

        assertEquals(RetryErrorCode.INVALID_ARGUMENT.getCode(), exception.getCode());
        assertEquals(RetryErrorCode.INVALID_ARGUMENT.getCode(), multiplierException.getCode());
        assertEquals(RetryErrorCode.INVALID_ARGUMENT.getCode(), jitterException.getCode());
    }

    /**
     * 验证不满足异常条件时立即停止，并保留原始异常原因。
     */
    @Test
    void shouldStopImmediatelyForNonRetryableException() {
        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("business failure");
        RetryPolicy<String> policy = ioRetryPolicy(Duration.ZERO);

        RetryOperationException exception = assertThrows(
                RetryOperationException.class,
                () -> RetryUtil.execute(() -> {
                    attempts.incrementAndGet();
                    throw failure;
                }, policy)
        );

        assertEquals(RetryErrorCode.EXECUTION_FAILED.getCode(), exception.getCode());
        assertEquals(1, attempts.get());
        assertSame(failure, exception.getCause());
    }

    /**
     * 验证可重试异常持续失败时准确执行最大次数并返回耗尽错误码。
     */
    @Test
    void shouldReportExhaustionAfterMaximumAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        IOException failure = new IOException("temporary failure");
        RetryPolicy<String> policy = ioRetryPolicy(Duration.ZERO);

        RetryOperationException exception = assertThrows(
                RetryOperationException.class,
                () -> RetryUtil.execute(() -> {
                    attempts.incrementAndGet();
                    throw failure;
                }, policy)
        );

        assertEquals(RetryErrorCode.EXHAUSTED.getCode(), exception.getCode());
        assertEquals(3, attempts.get());
        assertSame(failure, exception.getCause());
    }

    /**
     * 验证结果满足重试条件时继续轮询，并返回首个合格结果。
     */
    @Test
    void shouldRetryByResultUntilAccepted() {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(3)
                .fixedDelay(Duration.ZERO)
                .retryOnResult("RUNNING"::equals)
                .build();

        String result = RetryUtil.execute(
                () -> attempts.incrementAndGet() < 3 ? "RUNNING" : "DONE",
                policy
        );

        assertEquals("DONE", result);
        assertEquals(3, attempts.get());
    }

    /**
     * 验证最后一次结果仍满足重试条件时返回耗尽错误码。
     */
    @Test
    void shouldReportExhaustionWhenLastResultStillRequiresRetry() {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(2)
                .fixedDelay(Duration.ZERO)
                .retryOnResult("RUNNING"::equals)
                .build();

        RetryOperationException exception = assertThrows(
                RetryOperationException.class,
                () -> RetryUtil.execute(() -> {
                    attempts.incrementAndGet();
                    return "RUNNING";
                }, policy)
        );

        assertEquals(RetryErrorCode.EXHAUSTED.getCode(), exception.getCode());
        assertEquals(2, attempts.get());
    }

    /**
     * 验证结果判断器自身失败时立即停止，避免把策略错误当成业务失败重复执行任务。
     */
    @Test
    void shouldStopWhenResultPredicateFails() {
        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException predicateFailure = new IllegalStateException("predicate failure");
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(3)
                .fixedDelay(Duration.ZERO)
                .retryOnResult(result -> {
                    throw predicateFailure;
                })
                .build();

        RetryOperationException exception = assertThrows(
                RetryOperationException.class,
                () -> RetryUtil.execute(() -> {
                    attempts.incrementAndGet();
                    return "result";
                }, policy)
        );

        assertEquals(RetryErrorCode.EXECUTION_FAILED.getCode(), exception.getCode());
        assertEquals(1, attempts.get());
        assertSame(predicateFailure, exception.getCause());
    }

    /**
     * 验证任务主动报告中断时不执行重试并恢复线程中断标记。
     */
    @Test
    void shouldStopWhenTaskIsInterrupted() {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy<String> policy = RetryPolicy.<String>builder()
                .maxAttempts(3)
                .fixedDelay(Duration.ZERO)
                .build();

        RetryOperationException exception = assertThrows(
                RetryOperationException.class,
                () -> RetryUtil.execute(() -> {
                    attempts.incrementAndGet();
                    throw new InterruptedException("cancelled");
                }, policy)
        );

        assertEquals(RetryErrorCode.INTERRUPTED.getCode(), exception.getCode());
        assertEquals(1, attempts.get());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    /**
     * 验证退避等待被中断时立即停止后续尝试并恢复中断标记。
     */
    @Test
    void shouldStopWhenRetryDelayIsInterrupted() {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy<String> policy = ioRetryPolicy(Duration.ofMillis(10));

        Thread.currentThread().interrupt();
        RetryOperationException exception = assertThrows(
                RetryOperationException.class,
                () -> RetryUtil.execute(() -> {
                    attempts.incrementAndGet();
                    throw new IOException("temporary failure");
                }, policy)
        );

        assertEquals(RetryErrorCode.INTERRUPTED.getCode(), exception.getCode());
        assertEquals(1, attempts.get());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    /**
     * 验证固定、指数、指数随机和按结果便捷入口可以直接服务业务调用。
     */
    @Test
    void shouldExposeCommonConveniencePolicies() {
        AtomicInteger fixedAttempts = new AtomicInteger();
        String fixed = RetryUtil.retry(() -> {
            if (fixedAttempts.incrementAndGet() == 1) {
                throw new IOException("temporary failure");
            }
            return "fixed";
        }, 2, Duration.ZERO, IOException.class::isInstance);

        AtomicInteger exponentialAttempts = new AtomicInteger();
        String exponential = RetryUtil.retryExponential(() -> {
            if (exponentialAttempts.incrementAndGet() == 1) {
                throw new IOException("temporary failure");
            }
            return "exponential";
        }, 2, Duration.ofMillis(1), 2.0, Duration.ofMillis(10));

        AtomicInteger resultAttempts = new AtomicInteger();
        String result = RetryUtil.retryByResult(
                () -> resultAttempts.incrementAndGet() == 1 ? "RUNNING" : "DONE",
                2,
                Duration.ZERO,
                "RUNNING"::equals
        );

        RetryPolicy<String> randomPolicy = RetryPolicy.<String>builder()
                .maxAttempts(2)
                .exponentialRandomBackoff(
                        Duration.ofMillis(1),
                        2.0,
                        0.5,
                        Duration.ofMillis(10))
                .build();

        assertEquals("fixed", fixed);
        assertEquals("exponential", exponential);
        assertEquals("DONE", result);
        assertEquals("random", RetryUtil.execute(() -> "random", randomPolicy));
    }

    /**
     * 创建最多尝试三次、仅重试 IO 异常的固定延迟策略。
     *
     * @param delay 两次尝试之间的固定等待时间
     * @return IO 异常重试策略
     */
    private static RetryPolicy<String> ioRetryPolicy(Duration delay) {
        return RetryPolicy.<String>builder()
                .maxAttempts(3)
                .fixedDelay(delay)
                .retryOnException(IOException.class::isInstance)
                .build();
    }
}
