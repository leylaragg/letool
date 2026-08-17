package io.github.leylaragg.letool.net.tcp;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link ConnectRetryPolicy} 连接重试策略测试。
 */
class ConnectRetryPolicyTest {

    /**
     * 验证无抖动时按照指数增长并受最大延迟约束。
     */
    @Test
    void shouldCalculateBoundedExponentialDelay() {
        ConnectRetryPolicy policy = new ConnectRetryPolicy(
                5,
                Duration.ofMillis(100),
                Duration.ofMillis(250),
                0);

        assertThat(policy.delayAfterFailure(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.delayAfterFailure(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.delayAfterFailure(3)).isEqualTo(Duration.ofMillis(250));
        assertThat(policy.delayAfterFailure(4)).isEqualTo(Duration.ofMillis(250));
    }

    /**
     * 验证非法尝试次数、延迟边界和抖动比例会被拒绝。
     */
    @Test
    void shouldRejectInvalidPolicy() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectRetryPolicy(
                        0,
                        Duration.ofMillis(1),
                        Duration.ofSeconds(1),
                        0))
                .withMessageContaining("maxAttempts");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectRetryPolicy(
                        2,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(1),
                        0))
                .withMessageContaining("maxDelay");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectRetryPolicy(
                        2,
                        Duration.ofMillis(1),
                        Duration.ofSeconds(1),
                        1.1))
                .withMessageContaining("jitter");
    }
}
