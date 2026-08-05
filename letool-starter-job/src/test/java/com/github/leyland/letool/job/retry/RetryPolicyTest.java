package com.github.leyland.letool.job.retry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link RetryPolicy} 饱和指数退避测试。
 */
class RetryPolicyTest {

    /**
     * 验证重试次数判断使用“最大额外执行次数”语义。
     */
    @Test
    void shouldUseAdditionalAttemptSemantics() {
        assertThat(RetryPolicy.shouldRetry(0, 2)).isTrue();
        assertThat(RetryPolicy.shouldRetry(1, 2)).isTrue();
        assertThat(RetryPolicy.shouldRetry(2, 2)).isFalse();
    }

    /**
     * 验证退避值按照显式上限饱和且不会数值溢出。
     */
    @Test
    void shouldCalculateSaturatedBackoff() {
        assertThat(RetryPolicy.getBackoffDelay(0, 100, 2.0, 10_000)).isEqualTo(100);
        assertThat(RetryPolicy.getBackoffDelay(1, 100, 2.0, 10_000)).isEqualTo(200);
        assertThat(RetryPolicy.getBackoffDelay(100, Long.MAX_VALUE, 10.0, 60_000)).isEqualTo(60_000);
    }

    /**
     * 验证非法退避参数不会被静默修正。
     */
    @Test
    void shouldRejectInvalidBackoffArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RetryPolicy.getBackoffDelay(-1, 100, 2.0, 1_000));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RetryPolicy.getBackoffDelay(0, -1, 2.0, 1_000));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RetryPolicy.getBackoffDelay(0, 100, 0, 1_000));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RetryPolicy.getBackoffDelay(0, 100, 2.0, 0));
    }
}
