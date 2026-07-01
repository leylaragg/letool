package com.github.leyland.letool.sms.core;

import com.github.leyland.letool.sms.config.SmsProperties;
import com.github.leyland.letool.sms.exception.SmsException;
import com.github.leyland.letool.sms.model.SmsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SmsTemplate Builder 构建器测试")
class SmsTemplateBuilderTest {

    private SmsTemplate smsTemplate;
    private SmsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SmsProperties();
        SmsProperties.RateLimit rateLimit = new SmsProperties.RateLimit();
        rateLimit.setEnabled(false);
        properties.setRateLimit(rateLimit);

        smsTemplate = new SmsTemplate(new MockProvider(), properties);
    }

    @Nested
    @DisplayName("Builder 基本功能测试")
    class BuilderBasicTests {

        @Test
        @DisplayName("builder() 应返回 Builder 实例")
        void builderShouldReturnBuilder() {
            assertNotNull(smsTemplate.builder());
        }

        @Test
        @DisplayName("链式调用 .to().template().param().send() 应返回成功结果")
        void chainedSendShouldReturnResult() {
            SmsResult result = smsTemplate.builder()
                    .to("13800138000")
                    .template("SMS_001")
                    .param("code", "1234")
                    .send();

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Builder 参数校验测试")
    class BuilderValidationTests {

        @Test
        @DisplayName("未设置 phone 应抛出异常")
        void missingPhoneShouldThrow() {
            SmsException ex = assertThrows(SmsException.class, () ->
                    smsTemplate.builder()
                            .template("SMS_001")
                            .send());
            assertTrue(ex.getMessage().contains("手机号"));
        }

        @Test
        @DisplayName("phone 为空字符串应抛出异常")
        void blankPhoneShouldThrow() {
            SmsException ex = assertThrows(SmsException.class, () ->
                    smsTemplate.builder()
                            .to("   ")
                            .template("SMS_001")
                            .send());
            assertTrue(ex.getMessage().contains("手机号"));
        }

        @Test
        @DisplayName("未设置 template 应抛出异常")
        void missingTemplateShouldThrow() {
            SmsException ex = assertThrows(SmsException.class, () ->
                    smsTemplate.builder()
                            .to("13800138000")
                            .send());
            assertTrue(ex.getMessage().contains("模板编码"));
        }

        @Test
        @DisplayName("template 为空字符串应抛出异常")
        void blankTemplateShouldThrow() {
            SmsException ex = assertThrows(SmsException.class, () ->
                    smsTemplate.builder()
                            .to("13800138000")
                            .template("  ")
                            .send());
            assertTrue(ex.getMessage().contains("模板编码"));
        }
    }

    @Nested
    @DisplayName("Builder 链式调用测试")
    class BuilderChainingTests {

        @Test
        @DisplayName("所有设置方法应返回 this")
        void allMethodsShouldReturnThis() {
            SmsTemplate.Builder builder = smsTemplate.builder();
            assertSame(builder, builder.to("13800138000"));
            assertSame(builder, builder.template("SMS_001"));
            assertSame(builder, builder.param("k", "v"));
        }

        @Test
        @DisplayName("param 可多次调用添加多个变量")
        void paramCanBeCalledMultipleTimes() {
            SmsResult result = smsTemplate.builder()
                    .to("13800138000")
                    .template("SMS_001")
                    .param("code", "1234")
                    .param("product", "Ailind")
                    .send();

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Builder params() 批量设置测试")
    class BuilderParamsTests {

        @Test
        @DisplayName("params(Map) 应批量添加变量")
        void paramsShouldAddAllVariables() {
            SmsResult result = smsTemplate.builder()
                    .to("13800138000")
                    .template("SMS_001")
                    .params(Map.of("code", "5678", "product", "Ailind"))
                    .send();

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("params(null) 不应报错")
        void paramsNullShouldNotThrow() {
            assertDoesNotThrow(() ->
                    smsTemplate.builder().params(null));
        }
    }

    private static class MockProvider implements SmsProvider {
        @Override
        public SmsResult send(String phone, String templateCode, Map<String, String> params) {
            return SmsResult.success("MOCK-" + System.currentTimeMillis());
        }

        @Override
        public SmsResult batchSend(List<String> phones, String templateCode, Map<String, String> params) {
            return SmsResult.success("MOCK-BATCH-" + System.currentTimeMillis());
        }

        @Override
        public String getProviderName() {
            return "Mock";
        }
    }
}
