package io.github.leylaragg.letool.mail.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MailException} 统一异常契约测试。
 */
@DisplayName("MailException 统一异常测试")
class MailExceptionTest {

    @Test
    @DisplayName("配置错误应使用稳定错误码和安全字段名")
    void shouldCreateConfigurationException() {
        MailException exception =
                MailException.configurationInvalid("accounts.primary");

        assertThat(exception.getCode()).isEqualTo("MAIL_001");
        assertThat(exception.getMessage()).contains("accounts.primary");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("请求错误应使用稳定错误码")
    void shouldCreateRequestException() {
        MailException exception = MailException.requestInvalid("recipients");

        assertThat(exception.getCode()).isEqualTo("MAIL_002");
        assertThat(exception.getMessage()).contains("recipients");
    }

    @Test
    @DisplayName("投递错误应保留原因且不泄露底层异常文本")
    void shouldCreateDeliveryExceptionWithoutLeakingCauseMessage() {
        IllegalStateException cause =
                new IllegalStateException("smtp://user:secret@example.com");

        MailException exception = MailException.deliveryFailed(cause);

        assertThat(exception.getCode()).isEqualTo("MAIL_003");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage())
                .doesNotContain("secret")
                .doesNotContain("example.com");
    }

    @Test
    @DisplayName("异步执行器不可用时应保留原因")
    void shouldCreateAsyncUnavailableException() {
        IllegalStateException cause = new IllegalStateException("executor closed");

        MailException exception = MailException.asyncUnavailable(cause);

        assertThat(exception.getCode()).isEqualTo("MAIL_004");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("需要原因的工厂方法应拒绝空原因")
    void shouldRejectNullCause() {
        assertThatThrownBy(() -> MailException.deliveryFailed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cause");
    }
}
