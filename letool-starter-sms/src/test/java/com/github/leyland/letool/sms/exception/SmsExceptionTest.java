package com.github.leyland.letool.sms.exception;

import com.github.leyland.letool.exception.core.SystemException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmsException} 统一异常契约测试。
 */
class SmsExceptionTest {

    /**
     * 验证短信异常保留稳定错误码和消息参数。
     */
    @Test
    void shouldExposeStableErrorCodeAndArguments() {
        SmsException exception = SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, "缺少签名");

        assertThat(exception).isInstanceOf(SystemException.class);
        assertThat(exception.getCode()).isEqualTo("SMS_CONFIG_INVALID");
        assertThat(exception.getMessageArgs()).containsExactly("缺少签名");
    }

    /**
     * 验证 SDK 原始原因链不会丢失。
     */
    @Test
    void shouldPreserveSdkCause() {
        RuntimeException cause = new RuntimeException("network");

        SmsException exception = SmsException.causedBy(SmsErrorCode.SEND_FAILED, cause, "aliyun");

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCode()).isEqualTo("SMS_SEND_FAILED");
    }
}
