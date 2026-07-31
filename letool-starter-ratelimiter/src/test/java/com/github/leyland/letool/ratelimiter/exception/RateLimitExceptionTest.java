package com.github.leyland.letool.ratelimiter.exception;

import com.github.leyland.letool.exception.core.BusinessException;
import com.github.leyland.letool.exception.core.SystemException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流模块统一异常测试。
 */
class RateLimitExceptionTest {

    /**
     * 正常限流拒绝应属于可由调用方处理的业务异常。
     */
    @Test
    void shouldRepresentRejectionAsBusinessException() {
        RateLimitException exception = RateLimitException.rejected("send-sms");

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getCode()).isEqualTo("RATE_LIMIT_001");
        assertThat(exception.getFallbackMessage()).contains("send-sms");
    }

    /**
     * 配置问题应属于需要排查的系统异常。
     */
    @Test
    void shouldRepresentConfigurationFailureAsSystemException() {
        RateLimitConfigurationException exception =
                RateLimitConfigurationException.invalid("policies.send-sms.threshold");

        assertThat(exception).isInstanceOf(SystemException.class);
        assertThat(exception.getCode()).isEqualTo("RATE_LIMIT_002");
        assertThat(exception.getFallbackMessage()).contains("policies.send-sms.threshold");
    }
}
