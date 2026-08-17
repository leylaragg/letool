package io.github.leylaragg.letool.mq.model;

import io.github.leylaragg.letool.mq.exception.MqException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MqMessage} 不可变消息契约测试。
 */
@DisplayName("MqMessage 不可变消息契约")
class MqMessageTest {

    /**
     * 验证消息会复制 Header，并对外暴露只读视图。
     */
    @Test
    @DisplayName("应防御性复制并冻结 Header")
    void shouldDefensivelyCopyAndFreezeHeaders() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("traceId", "trace-1");

        MqMessage<String> message = new MqMessage<>("payload", headers, "application/json");
        headers.put("changed", "value");

        assertThat(message.headers())
                .containsEntry("traceId", "trace-1")
                .doesNotContainKey("changed");
        assertThatThrownBy(() -> message.headers().put("illegal", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证消息正文不会被默认字符串表示泄露。
     */
    @Test
    @DisplayName("toString 不应输出消息正文")
    void toStringShouldNotExposePayload() {
        MqMessage<String> message = new MqMessage<>(
                "secret-body",
                Map.of("traceId", "trace-1"),
                "application/json");

        assertThat(message.toString())
                .contains("application/json")
                .doesNotContain("secret-body")
                .doesNotContain("trace-1");
    }

    /**
     * 验证空消息正文会得到稳定的请求错误码。
     */
    @Test
    @DisplayName("消息正文为空时应拒绝")
    void shouldRejectNullPayload() {
        assertThatThrownBy(() -> new MqMessage<>(null, Map.of(), null))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_004"));
    }

    /**
     * 验证框架只读 Header 不能由调用方覆盖。
     */
    @Test
    @DisplayName("应拒绝框架只读 Header")
    void shouldRejectReadOnlyHeaders() {
        assertThatThrownBy(() -> new MqMessage<>("payload", Map.of("id", "custom-id"), null))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_007"));
        assertThatThrownBy(() -> new MqMessage<>("payload", Map.of("timestamp", 1L), null))
                .isInstanceOf(MqException.class);
    }

    /**
     * 验证 Header 名称和值必须完整。
     */
    @Test
    @DisplayName("应拒绝空 Header 名称和值")
    void shouldRejectInvalidHeaderEntry() {
        Map<String, Object> nullValueHeaders = new LinkedHashMap<>();
        nullValueHeaders.put("traceId", null);

        assertThatThrownBy(() -> new MqMessage<>("payload", Map.of(" ", "value"), null))
                .isInstanceOf(MqException.class);
        assertThatThrownBy(() -> new MqMessage<>("payload", nullValueHeaders, null))
                .isInstanceOf(MqException.class);
    }

    /**
     * 验证空白 Content-Type 会被拒绝，避免配置静默失效。
     */
    @Test
    @DisplayName("应拒绝空白 Content-Type")
    void shouldRejectBlankContentType() {
        assertThatThrownBy(() -> new MqMessage<>("payload", Map.of(), " "))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_004"));
    }
}
