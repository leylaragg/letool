package com.github.leyland.letool.oss.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 官方客户端配置。
 */
@ConfigurationProperties(prefix = "letool.oss.minio")
public class MinioOssProperties {

    /** MinIO 服务地址。 */
    private String endpoint;

    /** 静态访问密钥。 */
    private String accessKey;

    /** 静态私密密钥。 */
    private String secretKey;

    /** 可选的服务地域。 */
    private String region;

    /**
     * 获取 MinIO 服务地址。
     *
     * @return 服务地址
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置 MinIO 服务地址。
     *
     * @param endpoint 服务地址
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 获取访问密钥。
     *
     * @return 访问密钥
     */
    public String getAccessKey() {
        return accessKey;
    }

    /**
     * 设置访问密钥。
     *
     * @param accessKey 访问密钥
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * 获取私密密钥。
     *
     * @return 私密密钥
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * 设置私密密钥。
     *
     * @param secretKey 私密密钥
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 获取服务地域。
     *
     * @return 地域；未配置时为 {@code null}
     */
    public String getRegion() {
        return region;
    }

    /**
     * 设置服务地域。
     *
     * @param region 服务地域
     */
    public void setRegion(String region) {
        this.region = region;
    }
}
