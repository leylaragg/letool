package io.github.leylaragg.letool.print.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打印资源异常的错误分类和脱敏测试。
 *
 * @author leyland
 */
class PrintResourceExceptionTest {

    /** 底层资源信息只进入原因链，公开消息保留稳定资源类别。 */
    @Test
    void shouldKeepCauseBehindSafeResourceMessage() {
        IllegalStateException cause = new IllegalStateException("secret-font-location");

        PrintResourceException exception = PrintResourceException.unavailable("pdf-font", cause);

        assertThat(exception.getErrorCode()).isSameAs(PrintErrorCode.RESOURCE_UNAVAILABLE);
        assertThat(exception).hasCause(cause);
        assertThat(exception.getMessage()).contains("PRINT_014", "pdf-font")
                .doesNotContain("secret-font-location");
    }

    /** 资源类别和原因链都是分类异常不可缺少的上下文。 */
    @Test
    void shouldRejectIncompleteResourceFailure() {
        assertThatThrownBy(() -> PrintResourceException.unavailable(" ", new IllegalStateException()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrintResourceException.unavailable("pdf-font", null))
                .isInstanceOf(NullPointerException.class);
    }
}
