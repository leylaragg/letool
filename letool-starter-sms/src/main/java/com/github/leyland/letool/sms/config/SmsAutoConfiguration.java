package com.github.leyland.letool.sms.config;

import com.github.leyland.letool.sms.core.LocalSmsRateLimiter;
import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.core.SmsRateLimiter;
import com.github.leyland.letool.sms.core.SmsTemplate;
import com.github.leyland.letool.sms.provider.MockSmsProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 短信核心 Spring Boot 自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(SmsProperties.class)
@ConditionalOnProperty(prefix = "letool.sms", name = "enabled", havingValue = "true")
public class SmsAutoConfiguration {

    /**
     * 显式启用时创建开发测试用 Mock Provider。
     *
     * @return Mock Provider
     */
    @Bean
    @ConditionalOnMissingBean(name = "mockSmsProvider")
    @ConditionalOnProperty(prefix = "letool.sms.mock", name = "enabled", havingValue = "true")
    public MockSmsProvider mockSmsProvider() {
        return new MockSmsProvider();
    }

    /**
     * 创建默认短信发送尝试限流器。
     *
     * <p>用户注册 {@link SmsRateLimiter} Bean 时本配置退让。</p>
     *
     * @param properties 短信配置
     * @return 本地或无操作限流器
     */
    @Bean
    @ConditionalOnMissingBean(SmsRateLimiter.class)
    public SmsRateLimiter smsRateLimiter(SmsProperties properties) {
        if (!properties.getRateLimit().isEnabled()) {
            return SmsRateLimiter.noOp();
        }
        return new LocalSmsRateLimiter(properties.getRateLimit());
    }

    /**
     * 创建统一短信操作模板。
     *
     * @param smsProviders 容器中的全部短信 Provider
     * @param properties 短信配置
     * @param rateLimiter 发送尝试限流器
     * @return 短信模板
     */
    @Bean
    @ConditionalOnMissingBean(SmsTemplate.class)
    public SmsTemplate smsTemplate(
            ObjectProvider<SmsProvider> smsProviders,
            SmsProperties properties,
            SmsRateLimiter rateLimiter) {
        List<SmsProvider> providers = smsProviders.orderedStream().toList();
        return new SmsTemplate(providers, properties, rateLimiter);
    }
}
