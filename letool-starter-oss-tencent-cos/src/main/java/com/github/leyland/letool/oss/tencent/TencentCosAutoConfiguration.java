package com.github.leyland.letool.oss.tencent;

import com.github.leyland.letool.oss.config.OssAutoConfiguration;
import com.github.leyland.letool.oss.core.OssProvider;
import com.qcloud.cos.COS;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.auth.COSCredentialsProvider;
import com.qcloud.cos.auth.COSStaticCredentialsProvider;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 腾讯云 COS 官方 SDK 自动配置。
 */
@AutoConfiguration(before = OssAutoConfiguration.class)
@ConditionalOnClass(COS.class)
@ConditionalOnProperty(prefix = "letool.oss", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "letool.oss", name = "provider", havingValue = "tencent-cos")
@EnableConfigurationProperties(TencentCosProperties.class)
public class TencentCosAutoConfiguration {

    /**
     * 创建可复用的腾讯云 COS 官方客户端。
     *
     * <p>业务注册 {@link COSCredentialsProvider} 时优先使用原生动态凭证；注册
     * {@link COSCredentials} 时可以替换静态凭证；注册 {@link ClientConfig} 时可以完整
     * 提供地域、连接超时、重试和代理等高级参数。</p>
     *
     * @param properties 腾讯云 COS 配置
     * @param nativeCredentialProviders 腾讯云原生动态凭证 Provider
     * @param credentialsProviders 业务凭证
     * @param clientConfigurations 业务客户端高级配置
     * @return 腾讯云 COS 客户端
    */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean({COS.class, OssProvider.class})
    public COS tencentCosClient(
            TencentCosProperties properties,
            ObjectProvider<COSCredentialsProvider> nativeCredentialProviders,
            ObjectProvider<COSCredentials> credentialsProviders,
            ObjectProvider<ClientConfig> clientConfigurations) {
        COSCredentialsProvider credentialsProvider = nativeCredentialProviders.getIfAvailable();
        if (credentialsProvider == null) {
            COSCredentials credentials = credentialsProviders.getIfAvailable();
            if (credentials == null) {
                credentials = createStaticCredentials(properties);
            }
            credentialsProvider = new COSStaticCredentialsProvider(credentials);
        }
        ClientConfig clientConfig = clientConfigurations.getIfAvailable();
        if (clientConfig == null) {
            String region = requireText(properties.getRegion(), "letool.oss.tencent-cos.region");
            clientConfig = createDefaultClientConfig(region);
        }
        return new COSClient(credentialsProvider, clientConfig);
    }

    /**
     * 根据 Letool 配置创建长期或临时静态凭证。
     *
     * @param properties 腾讯云 COS 配置
     * @return 腾讯云静态凭证
     */
    private COSCredentials createStaticCredentials(TencentCosProperties properties) {
        String secretId = requireText(
                properties.getSecretId(),
                "letool.oss.tencent-cos.secret-id");
        String secretKey = requireText(
                properties.getSecretKey(),
                "letool.oss.tencent-cos.secret-key");
        if (properties.getSessionToken() == null || properties.getSessionToken().isBlank()) {
            return new BasicCOSCredentials(secretId, secretKey);
        }
        return new BasicSessionCredentials(
                secretId,
                secretKey,
                properties.getSessionToken());
    }

    /**
     * 创建适合 Spring 生命周期管理的默认客户端配置。
     *
     * <p>应用正常关闭时无需输出完整调用栈，关闭失败仍由 SDK 日志记录。</p>
     *
     * @param region COS 地域标识
     * @return 默认客户端配置
     */
    private ClientConfig createDefaultClientConfig(String region) {
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setPrintShutdownStackTrace(false);
        return clientConfig;
    }

    /**
     * 创建腾讯云 COS Provider。
     *
     * @param cosClient 腾讯云官方客户端
     * @return 腾讯云 COS Provider
     */
    @Bean
    @ConditionalOnMissingBean(OssProvider.class)
    public TencentCosProvider tencentCosProvider(COS cosClient) {
        return new TencentCosProvider(cosClient);
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
