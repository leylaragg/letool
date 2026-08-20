package io.github.leylaragg.letool.print.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 调用方输出目标故障的安全异常边界测试。
 *
 * @author leyland
 */
class PrintOutputExceptionTest {

    /** 输出异常必须保留原因链，同时只使用稳定错误码作为公开消息。 */
    @Test
    void shouldKeepOutputCauseWithoutExposingItsMessage() {
        IOException cause = new IOException("secret-output-detail");

        assertThatThrownBy(() -> {
            throw PrintOutputException.writeFailed(cause);
        })
                .isInstanceOf(PrintOutputException.class)
                .hasMessageContaining("PRINT_013")
                .hasMessageNotContaining("secret-output-detail")
                .hasCause(cause);
    }

    /** 没有原因的输出故障无法排查，因此工厂必须明确拒绝。 */
    @Test
    void shouldRejectMissingOutputCause() {
        assertThatThrownBy(() -> PrintOutputException.writeFailed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cause");
    }
}
