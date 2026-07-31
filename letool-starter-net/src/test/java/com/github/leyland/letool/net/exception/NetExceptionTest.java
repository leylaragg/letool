package com.github.leyland.letool.net.exception;

import com.github.leyland.letool.exception.core.SystemException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网络模块统一异常契约测试。
 */
class NetExceptionTest {

    /**
     * 验证网络异常继承统一系统异常并保留稳定错误码和原因链。
     */
    @Test
    void shouldRetainStructuredErrorCodeAndCause() {
        IllegalStateException cause = new IllegalStateException("connection reset");

        NetException exception = NetException.causedBy(
                NetErrorCode.CONNECT_FAILED,
                cause,
                "127.0.0.1",
                9000);

        assertThat(exception).isInstanceOf(SystemException.class);
        assertThat(exception.getCode()).isEqualTo("NET_TCP_CONNECT_FAILED");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).doesNotContain("connection reset");
    }
}
