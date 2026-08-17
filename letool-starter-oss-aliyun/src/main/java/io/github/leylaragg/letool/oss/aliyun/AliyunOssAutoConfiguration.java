package io.github.leylaragg.letool.oss.aliyun;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import io.github.leylaragg.letool.oss.config.OssAutoConfiguration;
import io.github.leylaragg.letool.oss.core.OssProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 阿里云 OSS 官方 SDK 自动配置。
 */
@AutoConfiguration(before = OssAutoConfiguration.class)
@ConditionalOnClass(OSS.class)
@ConditionalOnProperty(prefix = "letool.oss", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "letool.oss", name = "provider", havingValue = "aliyun")
@EnableConfigurationProperties(AliyunOssProperties.class)
public class AliyunOssAutoConfiguration {

    /**
     * 创建可复用的阿里云 OSS 官方客户端。
     *
     * <p>业务注册 {@link CredentialsProvider} 时优先使用动态凭证；注册
     * {@link ClientBuilderConfiguration} 时可以覆盖超时、连接池和代理等高级参数。</p>
     *
     * @param properties 阿里云 OSS 配置
     * @param credentialsProviders 业务凭证 Provider
     * @param clientConfigurations 业务客户端高级配置
     * @return 阿里云 OSS 客户端
    */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean({OSS.class, OssProvider.class})
    public OSS aliyunOssClient(
            AliyunOssProperties properties,
            ObjectProvider<CredentialsProvider> credentialsProviders,
            ObjectProvider<ClientBuilderConfiguration> clientConfigurations) {
        String endpoint = requireText(properties.getEndpoint(), "letool.oss.aliyun.endpoint");
        CredentialsProvider credentialsProvider = credentialsProviders.getIfAvailable();
        if (credentialsProvider == null) {
            String accessKeyId = requireText(
                    properties.getAccessKeyId(),
                    "letool.oss.aliyun.access-key-id");
            String accessKeySecret = requireText(
                    properties.getAccessKeySecret(),
                    "letool.oss.aliyun.access-key-secret");
            if (properties.getSecurityToken() == null || properties.getSecurityToken().isBlank()) {
                credentialsProvider = new DefaultCredentialProvider(accessKeyId, accessKeySecret);
            } else {
                credentialsProvider = new DefaultCredentialProvider(
                        accessKeyId,
                        accessKeySecret,
                        properties.getSecurityToken());
            }
        }
        ClientBuilderConfiguration configuration = clientConfigurations.getIfAvailable(
                ClientBuilderConfiguration::new);
        return new OSSClientBuilder().build(endpoint, credentialsProvider, configuration);
    }

    /**
     * 创建阿里云 OSS Provider。
     *
     * @param ossClient 阿里云官方客户端
     * @return 阿里云 Provider
     */
    @Bean
    @ConditionalOnMissingBean(OssProvider.class)
    public AliyunOssProvider aliyunOssProvider(OSS ossClient) {
        return new AliyunOssProvider(ossClient);
    }

    /**
     * 校验客户端必填配置。
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
}
