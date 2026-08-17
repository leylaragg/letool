package io.github.leylaragg.letool.sms.aliyun;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import io.github.leylaragg.letool.sms.config.SmsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 阿里云短信 V2 官方 SDK 自动配置。
 */
@AutoConfiguration(before = SmsAutoConfiguration.class)
@ConditionalOnClass(Client.class)
@ConditionalOnProperty(prefix = "letool.sms", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "letool.sms.aliyun", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AliyunSmsProperties.class)
public class AliyunSmsAutoConfiguration {

    /**
     * 创建可复用的阿里云短信官方客户端。
     *
     * <p>未配置静态 AccessKey 时使用阿里云默认凭证链。业务注册 {@link Client} Bean
     * 后本配置退让。</p>
     *
     * @param properties 阿里云短信配置
     * @return 阿里云短信客户端
     * @throws Exception 官方 SDK 初始化失败时抛出
     */
    @Bean
    @ConditionalOnMissingBean(value = Client.class, name = "aliyunSmsProvider")
    public Client aliyunSmsClient(AliyunSmsProperties properties) throws Exception {
        Config config = new Config();
        boolean hasAccessKeyId = hasText(properties.getAccessKeyId());
        boolean hasAccessKeySecret = hasText(properties.getAccessKeySecret());
        if (hasAccessKeyId != hasAccessKeySecret) {
            throw new IllegalStateException("letool.sms.aliyun.access-key-id 与 access-key-secret 必须同时配置");
        }
        if (hasAccessKeyId) {
            com.aliyun.credentials.models.Config credentialConfig =
                    new com.aliyun.credentials.models.Config()
                            .setAccessKeyId(properties.getAccessKeyId())
                            .setAccessKeySecret(properties.getAccessKeySecret());
            if (hasText(properties.getSecurityToken())) {
                credentialConfig.setSecurityToken(properties.getSecurityToken()).setType("sts");
            } else {
                credentialConfig.setType("access_key");
            }
            config.setCredential(new com.aliyun.credentials.Client(credentialConfig));
        } else {
            config.setCredential(new com.aliyun.credentials.Client());
        }
        config.setEndpoint(requireText(properties.getEndpoint(), "letool.sms.aliyun.endpoint"));
        config.setRegionId(requireText(properties.getRegionId(), "letool.sms.aliyun.region-id"));
        return new Client(config);
    }

    /**
     * 创建阿里云短信 Provider。
     *
     * @param client 阿里云短信客户端
     * @param properties 阿里云短信配置
     * @return 阿里云短信 Provider
     */
    @Bean
    @ConditionalOnMissingBean(name = "aliyunSmsProvider")
    public AliyunSmsProvider aliyunSmsProvider(Client client, AliyunSmsProperties properties) {
        return new AliyunSmsProvider(client, properties);
    }

    /**
     * 判断文本是否有效。
     *
     * @param value 待检查文本
     * @return 有效时返回 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 校验必填配置文本。
     *
     * @param value 配置值
     * @param propertyName 配置项名称
     * @return 已校验配置值
     */
    private String requireText(String value, String propertyName) {
        if (!hasText(value)) {
            throw new IllegalStateException(propertyName + " 不能为空");
        }
        return value;
    }
}
