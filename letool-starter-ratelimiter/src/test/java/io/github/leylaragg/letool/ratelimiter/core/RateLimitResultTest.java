package io.github.leylaragg.letool.ratelimiter.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RateLimitResult} 值对象测试。
 */
class RateLimitResultTest {

    /**
     * 放行结果不应携带虚构的阻断原因。
     */
    @Test
    void shouldCreateAllowedResult() {
        RateLimitResult result = RateLimitResult.allowed();

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getBlockReason()).isNull();
    }

    /**
     * 拒绝结果应保留 Sentinel 提供的稳定阻断类型。
     */
    @Test
    void shouldCreateRejectedResult() {
        RateLimitResult result = RateLimitResult.rejected("ParamFlowException");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getBlockReason()).isEqualTo("ParamFlowException");
    }
}
