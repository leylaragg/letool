package com.github.leyland.letool.oss.config;

import com.github.leyland.letool.oss.core.OssProvider;
import com.github.leyland.letool.oss.core.OssTemplate;
import com.github.leyland.letool.oss.exception.OssException;
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
 * OSS 对象存储模块自动配置类，负责创建统一操作入口 {@link OssTemplate}，
 * 并在明确允许时创建内置 stub {@link OssProvider}。
 *
 * <p>当前版本内置的 Aliyun OSS、Tencent COS、MinIO provider 均为 stub，
 * 不会访问真实对象存储。为了符合工具包边界，OSS starter 默认不启用；
 * 启用后如果没有业务项目自定义 {@link OssProvider}，必须显式设置
 * {@code letool.oss.stub-enabled=true} 才会创建内置 stub provider。</p>
 *
 * <p><b>Bean 创建策略：</b></p>
 * <ul>
 *   <li>{@code ossProvider} — 仅在 {@code letool.oss.stub-enabled=true} 时，根据
 *       {@code letool.oss.default-provider} 创建阿里云 OSS、MinIO 或腾讯云 COS 的 stub provider。</li>
 *   <li>{@code ossTemplate} — OSS 操作统一模板，封装默认 Bucket 和 Builder 模式，简化调用。</li>
 * </ul>
 *
 * <p>通过 {@code letool.oss.enabled=true} 可显式启用整个 OSS 模块。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnProperty(prefix = "letool.oss", name = "enabled", havingValue = "true")
public class OssAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration.class);

    // ======================== Bean 定义 ========================

    /**
     * 创建 OSS 提供者 Bean。
     *
     * <p>当前内置实现均为 stub，因此只有在 {@code letool.oss.stub-enabled=true}
     * 时才会实例化：
     * <ul>
     *   <li>{@code aliyun} — {@link AliyunOssProvider} stub</li>
     *   <li>{@code tencent-cos} — {@link TencentCosProvider} stub</li>
     *   <li>{@code minio}（默认）— {@link MinioProvider} stub</li>
     * </ul>
     * 当项目中已存在 {@link OssProvider} Bean 时，此方法不会覆盖，业务项目可自行接入真实 SDK。
     * </p>
     *
     * @param properties OSS 模块配置属性
     * @return 对应存储类型的 OssProvider 实现
     */
    @Bean
    @ConditionalOnMissingBean
    public OssProvider ossProvider(OssProperties properties) {
        String provider = properties.getDefaultProvider();
        if (!properties.isStubEnabled()) {
            throw stubModeDisabled(provider);
        }

        log.warn("[letool-oss] Initializing {} OSS provider in STUB mode; no real object storage API calls will be made",
                provider);
        switch (provider.toLowerCase()) {
            case "aliyun":
                return new AliyunOssProvider(properties.getAliyun());
            case "tencent-cos":
                return new TencentCosProvider(properties.getTencentCos());
            case "minio":
                return new MinioProvider(properties.getMinio());
            default:
                throw unsupportedProvider(provider);
        }
    }

    /**
     * 构建未显式启用 stub 模式时的配置错误。
     *
     * @param provider 当前配置的 OSS provider
     * @return OSS 配置异常
     */
    private OssException stubModeDisabled(String provider) {
        return new OssException("[letool-oss] " + provider + " 当前为内置 stub provider，未启用 stub 模式；"
                + "如需开发演示请设置 letool.oss.stub-enabled=true，"
                + "如需生产接入请在业务项目中注册自定义 OssProvider Bean。");
    }

    /**
     * 构建未知 OSS provider 的配置错误。
     *
     * @param provider 当前配置的 OSS provider
     * @return OSS 配置异常
     */
    private OssException unsupportedProvider(String provider) {
        return new OssException("[letool-oss] " + provider
                + " 是不支持的 OSS provider；可选值为 aliyun、minio、tencent-cos。");
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
