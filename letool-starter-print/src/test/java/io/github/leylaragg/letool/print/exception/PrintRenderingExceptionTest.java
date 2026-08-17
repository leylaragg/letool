package io.github.leylaragg.letool.print.exception;

import io.github.leylaragg.letool.print.api.OutputFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 等文档渲染器共用的安全异常边界测试。
 *
 * @author leyland
 */
class PrintRenderingExceptionTest {

    /** 第三方故障保留原因链，但不会把原始消息带到用户可见异常中。 */
    @Test
    void shouldKeepRenderingCauseWithoutExposingItsMessage() {
        IllegalStateException cause = new IllegalStateException("secret-rendering-detail");

        assertThatThrownBy(() -> {
            throw PrintRenderingException.renderFailed(OutputFormat.PDF, cause);
        })
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_010")
                .hasMessageContaining("pdf")
                .hasMessageNotContaining("secret-rendering-detail")
                .hasCause(cause);
    }

    /** 页数和产物大小超限分别使用稳定的治理错误码。 */
    @Test
    void shouldDescribeRenderingLimitsWithSafeArguments() {
        assertThatThrownBy(() -> {
            throw PrintRenderingException.pageLimitExceeded(20);
        })
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_011")
                .hasMessageContaining("20");

        IllegalStateException cause = new IllegalStateException("secret-output-detail");
        assertThatThrownBy(() -> {
            throw PrintRenderingException.outputLimitExceeded(1_048_576L, cause);
        })
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_007")
                .hasMessageNotContaining("secret-output-detail")
                .hasCause(cause)
                .satisfies(exception -> {
                    PrintRenderingException renderingException = (PrintRenderingException) exception;
                    assertThat(renderingException.getMessageArgs()).containsExactly(1_048_576L);
                });
    }

    /** 渲染异常工厂拒绝丢失底层原因，避免生成无法排查的系统故障。 */
    @Test
    void shouldRejectMissingRenderingCause() {
        assertThatThrownBy(() -> PrintRenderingException.renderFailed(OutputFormat.PDF, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cause");
        assertThatThrownBy(() -> PrintRenderingException.outputLimitExceeded(1024, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cause");
    }
}
