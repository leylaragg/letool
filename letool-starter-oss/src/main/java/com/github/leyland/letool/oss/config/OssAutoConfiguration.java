package com.github.leyland.letool.oss.config;

import com.github.leyland.letool.oss.core.OssProvider;
import com.github.leyland.letool.oss.core.OssTemplate;
import com.github.leyland.letool.oss.provider.AliyunOssProvider;
import com.github.leyland.letool.oss.provider.MinioProvider;
import com.github.leyland.letool.oss.provider.TencentCosProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OSS 对象存储模块自动配置类，负责根据 {@link OssProperties} 中的配置创建对应的
 * {@link OssProvider} 实现和统一操作入口 {@link OssTemplate}。
 *
 * <p><b>Bean 创建策略：</b></p>
 * <ul>
 *   <li>{@code ossProvider} — 根据 {@code letool.oss.default-provider} 配置决定创建
 *       阿里云 OSS、MinIO 还是腾讯云 COS 的 Provider 实现。默认使用 {@link MinioProvider}。</li>
 *   <li>{@code ossTemplate} — OSS 操作统一模板，封装默认 Bucket 和 Builder 模式，简化调用。</li>
 * </ul>
 *
 * <p>通过 {@code letool.oss.enabled=false} 可禁用整个 OSS 模块。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnProperty(prefix = "letool.oss", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OssAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration.class);

    // ======================== Bean 定义 ========================

    /**
     * 创建 OSS 提供者 Bean。
     *
     * <p>根据 {@code letool.oss.default-provider} 配置决定实例化哪种存储实现：
     * <ul>
     *   <li>{@code aliyun} — {@link AliyunOssProvider}</li>
     *   <li>{@code tencent-cos} — {@link TencentCosProvider}</li>
     *   <li>{@code minio}（默认）— {@link MinioProvider}</li>
     * </ul>
     * 当项目中已存在同名 Bean 时，此方法不会覆盖。
     * </p>
     *
     * @param properties OSS 模块配置属性
     * @return 对应存储类型的 OssProvider 实现
     */
    @Bean
    @ConditionalOnMissingBean
    public OssProvider ossProvider(OssProperties properties) {
        String provider = properties.getDefaultProvider();
        log.info("Initializing OSS provider: {}", provider);
        switch (provider.toLowerCase()) {
            case "aliyun":
                return new AliyunOssProvider(properties.getAliyun());
            case "tencent-cos":
                return new TencentCosProvider(properties.getTencentCos());
            case "minio":
            default:
                return new MinioProvider(properties.getMinio());
        }
    }

    /**
     * 创建 OSS 操作模板 Bean。
     *
     * <p>{@link OssTemplate} 封装了默认 Bucket 设置和 Builder 模式，是业务代码操作
     * 对象存储的推荐入口。它持有 {@link OssProvider} 和 {@link OssProperties}，
     * 在未指定 Bucket 时会自动使用配置中的默认 Bucket。</p>
     *
     * @param ossProvider OSS 存储提供者
     * @param properties  OSS 模块配置属性（用于获取默认 Bucket）
     * @return OssTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OssTemplate ossTemplate(OssProvider ossProvider, OssProperties properties) {
        return new OssTemplate(ossProvider, properties);
    }
}
