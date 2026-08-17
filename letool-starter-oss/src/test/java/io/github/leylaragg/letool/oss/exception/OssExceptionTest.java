package io.github.leylaragg.letool.oss.exception;

import io.github.leylaragg.letool.exception.core.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * OSS 统一异常测试。
 */
class OssExceptionTest {

    /**
     * 验证 OSS 异常接入统一系统异常体系并保留底层原因。
     */
    @Test
    @DisplayName("OSS 异常应保留结构化错误码和原因链")
    void shouldKeepErrorCodeAndCause() {
        IllegalStateException cause = new IllegalStateException("底层连接失败");

        OssException exception = OssException.causedBy(
                OssErrorCode.UPLOAD_FAILED,
                cause,
                "minio",
                "assets",
                "images/avatar.png");

        assertThat(exception).isInstanceOf(SystemException.class);
        assertThat(exception.getCode()).isEqualTo("OSS_UPLOAD_FAILED");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessageArgs())
                .containsExactly("minio", "assets", "images/avatar.png");
    }

    /**
     * 验证创建带原因异常时不允许丢失原因对象。
     */
    @Test
    @DisplayName("OSS 异常应拒绝空原因")
    void shouldRejectNullCause() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OssException.causedBy(OssErrorCode.DOWNLOAD_FAILED, null, "minio", "a", "b"))
                .withMessageContaining("cause");
    }
}
