package io.github.leylaragg.letool.sms.core;

import io.github.leylaragg.letool.sms.config.SmsProperties;
import io.github.leylaragg.letool.sms.exception.SmsException;
import io.github.leylaragg.letool.sms.model.SmsRecipientResult;
import io.github.leylaragg.letool.sms.model.SmsRequest;
import io.github.leylaragg.letool.sms.model.SmsResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 短信核心生产契约测试。
 */
class SmsProductionContractTest {

    /**
     * 验证请求会保存手机号和有序模板参数的不可变快照。
     */
    @Test
    void shouldCreateImmutableRequestSnapshotWithOrderedParameters() {
        List<String> phones = new ArrayList<>(List.of("+8613800138000"));
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("code", "1234");
        parameters.put("minutes", "5");

        SmsRequest request = SmsRequest.builder()
                .phones(phones)
                .templateCode("SMS_VERIFY")
                .parameters(parameters)
                .build();
        phones.add("+8613900139000");
        parameters.put("ignored", "value");

        assertThat(request.getPhones()).containsExactly("+8613800138000");
        assertThat(request.getParameters())
                .extracting(parameter -> parameter.getName() + "=" + parameter.getValue())
                .containsExactly("code=1234", "minutes=5");
        assertThatThrownBy(() -> request.getPhones().add("+8613700137000"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证单次请求可以覆盖默认 Provider，并保持便捷路由入口。
     */
    @Test
    void shouldRouteRequestToExplicitProvider() {
        RecordingProvider aliyun = new RecordingProvider("aliyun");
        RecordingProvider tencent = new RecordingProvider("tencent");
        SmsProperties properties = new SmsProperties();
        properties.setDefaultProvider("tencent");
        SmsTemplate template = new SmsTemplate(
                List.of(aliyun, tencent),
                properties,
                SmsRateLimiter.noOp());

        SmsResult result = template.send(SmsRequest.builder()
                .phone("+8613800138000")
                .templateCode("SMS_VERIFY")
                .parameter("code", "1234")
                .provider("aliyun")
                .build());

        assertThat(result.getProvider()).isEqualTo("aliyun");
        assertThat(aliyun.requests).hasSize(1);
        assertThat(tencent.requests).isEmpty();
    }

    /**
     * 验证多个 Provider 且没有默认值时会快速失败。
     */
    @Test
    void shouldFailWhenMultipleProvidersHaveNoDefault() {
        SmsTemplate template = new SmsTemplate(
                List.of(new RecordingProvider("aliyun"), new RecordingProvider("tencent")),
                new SmsProperties(),
                SmsRateLimiter.noOp());

        assertThatThrownBy(() -> template.send(SmsRequest.builder()
                .phone("+8613800138000")
                .templateCode("SMS_VERIFY")
                .build()))
                .isInstanceOf(SmsException.class)
                .hasMessageContaining("default-provider");
    }

    /**
     * 验证批量结果能够表达部分手机号失败。
     */
    @Test
    void shouldRepresentPartialRecipientFailure() {
        SmsResult result = SmsResult.fromRecipients(
                "tencent",
                "request-1",
                "PARTIAL_FAILURE",
                "部分手机号发送失败",
                List.of(
                        SmsRecipientResult.success("+8613800138000", "Ok", "发送成功"),
                        SmsRecipientResult.failure("+8613900139000", "LIMIT_EXCEEDED", "超过频率限制")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getRecipientResults()).hasSize(2);
        assertThat(result.getRecipientResults().get(1).getCode()).isEqualTo("LIMIT_EXCEEDED");
    }

    /**
     * 用于验证 Provider 路由的记录型实现。
     */
    private static final class RecordingProvider implements SmsProvider {

        private final String providerName;
        private final List<SmsRequest> requests = new ArrayList<>();

        /**
         * 创建记录型 Provider。
         *
         * @param providerName Provider 名称
         */
        private RecordingProvider(String providerName) {
            this.providerName = providerName;
        }

        /**
         * 记录请求并返回成功结果。
         *
         * @param request 短信请求
         * @return 成功结果
         */
        @Override
        public SmsResult send(SmsRequest request) {
            requests.add(request);
            List<SmsRecipientResult> recipients = request.getPhones().stream()
                    .map(phone -> SmsRecipientResult.success(phone, "OK", "发送成功"))
                    .toList();
            return SmsResult.fromRecipients(providerName, "request", "OK", "发送成功", recipients);
        }

        /**
         * 获取 Provider 名称。
         *
         * @return Provider 名称
         */
        @Override
        public String getProviderName() {
            return providerName;
        }
    }
}
