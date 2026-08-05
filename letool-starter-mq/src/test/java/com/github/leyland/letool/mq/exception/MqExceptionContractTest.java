package com.github.leyland.letool.mq.exception;

import com.github.leyland.letool.exception.core.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MQ 统一异常契约测试。
 */
@DisplayName("MQ 统一异常契约")
class MqExceptionContractTest {

    /**
     * 验证所有 MQ 错误码稳定且不重复。
     */
    @Test
    @DisplayName("错误码应稳定且唯一")
    void errorCodesShouldBeStableAndUnique() {
        assertThat(Arrays.stream(MqErrorCode.values()).map(MqErrorCode::getCode))
                .containsExactly("MQ_001", "MQ_002", "MQ_003", "MQ_004",
                        "MQ_005", "MQ_006", "MQ_007", "MQ_008")
                .doesNotHaveDuplicates();
    }

    /**
     * 验证 MQ 异常统一继承系统异常。
     */
    @Test
    @DisplayName("MqException 应继承 SystemException")
    void shouldExtendSystemException() {
        MqException exception = MqException.of(MqErrorCode.CONFIGURATION_INVALID, "缺少 Provider");

        assertThat(exception).isInstanceOf(SystemException.class);
        assertThat(exception.getErrorCode()).isEqualTo(MqErrorCode.CONFIGURATION_INVALID);
        assertThat(exception.getMessage()).contains("缺少 Provider");
    }

    /**
     * 验证异常工厂保留底层原因链。
     */
    @Test
    @DisplayName("causedBy 应保留底层异常")
    void causedByShouldPreserveCause() {
        IllegalStateException cause = new IllegalStateException("broker unavailable");

        MqException exception = MqException.causedBy(
                MqErrorCode.SEND_FAILED,
                cause,
                "rabbit");

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).doesNotContain("broker unavailable");
    }

    /**
     * 验证 causedBy 不接受空原因。
     */
    @Test
    @DisplayName("causedBy 应拒绝空原因")
    void causedByShouldRejectNullCause() {
        assertThatThrownBy(() -> MqException.causedBy(
                MqErrorCode.SEND_FAILED,
                null,
                "rabbit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cause");
    }
}
