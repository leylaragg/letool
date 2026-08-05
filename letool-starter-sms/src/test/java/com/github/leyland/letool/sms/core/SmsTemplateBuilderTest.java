package com.github.leyland.letool.sms.core;

import com.github.leyland.letool.sms.config.SmsProperties;
import com.github.leyland.letool.sms.model.SmsResult;
import com.github.leyland.letool.sms.provider.MockSmsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SmsTemplate.Builder} 便捷 API 测试。
 */
class SmsTemplateBuilderTest {

    private SmsTemplate smsTemplate;

    /**
     * 创建关闭限流的 Mock 短信模板。
     */
    @BeforeEach
    void setUp() {
        SmsProperties properties = new SmsProperties();
        properties.getRateLimit().setEnabled(false);
        smsTemplate = new SmsTemplate(new MockSmsProvider(), properties);
    }

    /**
     * 验证链式 API 可以发送单条短信。
     */
    @Test
    void shouldSendWithFluentBuilder() {
        SmsResult result = smsTemplate.builder()
                .to("+8613800138000")
                .template("SMS_VERIFY")
                .param("code", "1234")
                .send();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProvider()).isEqualTo("mock");
    }

    /**
     * 验证 Builder 支持选择 Provider、签名和批量参数。
     */
    @Test
    void shouldSupportProviderSignAndBulkParameters() {
        SmsResult result = smsTemplate.builder()
                .to("+8613800138000")
                .provider("mock")
                .signName("测试签名")
                .template("SMS_VERIFY")
                .params(Map.of("code", "1234"))
                .send();

        assertThat(result.isSuccess()).isTrue();
    }

    /**
     * 验证手机号为空时拒绝发送。
     */
    @Test
    void shouldRejectMissingPhone() {
        assertThatThrownBy(() -> smsTemplate.builder()
                .template("SMS_VERIFY")
                .send())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("手机号");
    }

    /**
     * 验证模板编码为空时拒绝发送。
     */
    @Test
    void shouldRejectMissingTemplateCode() {
        assertThatThrownBy(() -> smsTemplate.builder()
                .to("+8613800138000")
                .send())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板编码");
    }
}
