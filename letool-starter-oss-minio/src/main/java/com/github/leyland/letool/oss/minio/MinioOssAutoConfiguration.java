package com.github.leyland.letool.oss.minio;

import com.github.leyland.letool.oss.config.OssAutoConfiguration;
import com.github.leyland.letool.oss.core.OssProvider;
import io.minio.MinioClient;
import io.minio.credentials.Provider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MinIO 官方 SDK 自动配置。
 */
@AutoConfiguration(before = OssAutoConfiguration.class)
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "letool.oss", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "letool.oss", name = "provider", havingValue = "minio", matchIfMissing = true)
@EnableConfigurationProperties(MinioOssProperties.class)
public class MinioOssAutoConfiguration {

    /**
     * 创建可复用的 MinIO 官方客户端。
     *
     * <p>业务提供 {@link Provider} 时优先使用动态凭证；否则使用配置中的静态密钥。
     * 业务也可以直接注册 {@link MinioClient} 完全接管客户端配置。</p>
     *
     * @param properties MinIO 配置
     * @param credentialProviders 业务自定义凭证 Provider
     * @return MinIO 客户端
    */
    @Bean
    @ConditionalOnMissingBean({MinioClient.class, OssProvider.class})
    public MinioClient minioClient(
            MinioOssProperties properties,
            ObjectProvider<Provider> credentialProviders) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(requireText(properties.getEndpoint(), "letool.oss.minio.endpoint"));
        Provider credentialProvider = credentialProviders.getIfAvailable();
        if (credentialProvider != null) {
            builder.credentialsProvider(credentialProvider);
        } else {
            builder.credentials(
                    requireText(properties.getAccessKey(), "letool.oss.minio.access-key"),
                    requireText(properties.getSecretKey(), "letool.oss.minio.secret-key"));
        }
        if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
            builder.region(properties.getRegion());
        }
        return builder.build();
    }

    /**
     * 创建 MinIO Provider。
     *
     * @param minioClient MinIO 官方客户端
     * @return MinIO Provider
     */
    @Bean
    @ConditionalOnMissingBean(OssProvider.class)
    public MinioOssProvider minioOssProvider(MinioClient minioClient) {
        return new MinioOssProvider(minioClient);
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
