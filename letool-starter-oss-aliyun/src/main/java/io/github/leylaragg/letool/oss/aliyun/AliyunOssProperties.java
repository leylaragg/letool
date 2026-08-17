package io.github.leylaragg.letool.oss.aliyun;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 官方客户端配置。
 */
@ConfigurationProperties(prefix = "letool.oss.aliyun")
public class AliyunOssProperties {

    /** OSS 服务 Endpoint。 */
    private String endpoint;

    /** RAM AccessKeyId。 */
    private String accessKeyId;

    /** RAM AccessKeySecret。 */
    private String accessKeySecret;

    /** 可选的临时安全令牌。 */
    private String securityToken;

    /**
     * 获取 OSS Endpoint。
     *
     * @return Endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置 OSS Endpoint。
     *
     * @param endpoint Endpoint
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 获取 AccessKeyId。
     *
     * @return AccessKeyId
     */
    public String getAccessKeyId() {
        return accessKeyId;
    }

    /**
     * 设置 AccessKeyId。
     *
     * @param accessKeyId AccessKeyId
     */
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    /**
     * 获取 AccessKeySecret。
     *
     * @return AccessKeySecret
     */
    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    /**
     * 设置 AccessKeySecret。
     *
     * @param accessKeySecret AccessKeySecret
     */
    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    /**
     * 获取临时安全令牌。
     *
     * @return 安全令牌；未配置时为 {@code null}
     */
    public String getSecurityToken() {
        return securityToken;
    }

    /**
     * 设置临时安全令牌。
     *
     * @param securityToken 临时安全令牌
     */
    public void setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
    }
}
