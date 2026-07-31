package com.github.leyland.letool.monitor.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MonitorException} 结构化错误码测试。
 */
class MonitorExceptionTest {

    /**
     * 验证无原因异常会保留稳定错误码和消息参数。
     */
    @Test
    void shouldExposeStableErrorCode() {
        MonitorException exception = MonitorException.of(
                MonitorErrorCode.CONFIGURATION_INVALID,
                "Cron 表达式为空");

        assertThat(exception.getCode())
                .isEqualTo("MONITOR_CONFIGURATION_INVALID");
        assertThat(exception.getMessageArgs())
                .containsExactly("Cron 表达式为空");
        assertThat(exception.getCause()).isNull();
    }

    /**
     * 验证带原因异常会保留底层原因链。
     */
    @Test
    void shouldExposeStableErrorCodeAndCause() {
        IllegalStateException cause = new IllegalStateException("底层失败");

        MonitorException exception = MonitorException.causedBy(
                MonitorErrorCode.CLEANUP_TASK_FAILED,
                cause,
                "archive");

        assertThat(exception.getCode())
                .isEqualTo("MONITOR_CLEANUP_TASK_FAILED");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    /**
     * 验证创建带原因异常时不允许传入空原因。
     */
    @Test
    void shouldRejectNullCause() {
        assertThatThrownBy(() -> MonitorException.causedBy(
                MonitorErrorCode.WEBHOOK_DELIVERY_FAILED,
                null,
                "DingTalk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cause 不能为空");
    }
}
