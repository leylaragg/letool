package io.github.leylaragg.letool.sms.tencent;

import io.github.leylaragg.letool.sms.config.SmsAutoConfiguration;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 腾讯云 SMS 3.0 官方 SDK 自动配置。
 */
@AutoConfiguration(before = SmsAutoConfiguration.class)
@ConditionalOnClass(SmsClient.class)
@ConditionalOnProperty(prefix = "letool.sms", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "letool.sms.tencent", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TencentSmsProperties.class)
public class TencentSmsAutoConfiguration {

    /**
     * 创建可复用的腾讯云短信官方客户端。
     *
     * <p>业务注册 {@link SmsClient} Bean 后本配置退让，可用于动态凭证、代理或更复杂的
     * 客户端配置。</p>
     *
     * @param properties 腾讯云短信配置
     * @return 腾讯云短信客户端
     */
    @Bean
    @ConditionalOnMissingBean(value = SmsClient.class, name = "tencentSmsProvider")
    public SmsClient tencentSmsClient(TencentSmsProperties properties) {
        Credential credential = new Credential(
                requireText(properties.getSecretId(), "letool.sms.tencent.secret-id"),
                requireText(properties.getSecretKey(), "letool.sms.tencent.secret-key"));
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(requireText(properties.getEndpoint(), "letool.sms.tencent.endpoint"));
        httpProfile.setConnTimeout(positive(properties.getConnectTimeoutSeconds(), "connect-timeout-seconds"));
        httpProfile.setReadTimeout(positive(properties.getReadTimeoutSeconds(), "read-timeout-seconds"));
        httpProfile.setWriteTimeout(positive(properties.getWriteTimeoutSeconds(), "write-timeout-seconds"));
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        return new SmsClient(
                credential,
                requireText(properties.getRegion(), "letool.sms.tencent.region"),
                clientProfile);
    }

    /**
     * 创建腾讯云短信 Provider。
     *
     * @param client 腾讯云短信客户端
     * @param properties 腾讯云短信配置
     * @return 腾讯云短信 Provider
     */
    @Bean
    @ConditionalOnMissingBean(name = "tencentSmsProvider")
    public TencentSmsProvider tencentSmsProvider(SmsClient client, TencentSmsProperties properties) {
        return new TencentSmsProvider(client, properties);
    }

    /**
     * 校验必填配置文本。
     *
     * @param value 配置值
     * @param propertyName 配置项名称
     * @return 已校验配置值
     */
    private String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " 不能为空");
        }
        return value;
    }

    /**
     * 校验正数超时配置。
     *
     * @param value 超时秒数
     * @param propertyName 配置项名称
     * @return 已校验超时秒数
     */
    private int positive(int value, String propertyName) {
        if (value <= 0) {
            throw new IllegalStateException("letool.sms.tencent." + propertyName + " 必须大于 0");
        }
        return value;
    }
}
