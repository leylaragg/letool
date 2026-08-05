package com.github.leyland.letool.oss.tencent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云 COS 官方客户端配置。
 */
@ConfigurationProperties(prefix = "letool.oss.tencent-cos")
public class TencentCosProperties {

    /** COS 所在地域，例如 {@code ap-guangzhou}。 */
    private String region;

    /** 腾讯云 SecretId。 */
    private String secretId;

    /** 腾讯云 SecretKey。 */
    private String secretKey;

    /** 可选的临时会话令牌。 */
    private String sessionToken;

    /**
     * 获取 COS 所在地域。
     *
     * @return 地域标识
     */
    public String getRegion() {
        return region;
    }

    /**
     * 设置 COS 所在地域。
     *
     * @param region 地域标识
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * 获取腾讯云 SecretId。
     *
     * @return SecretId
     */
    public String getSecretId() {
        return secretId;
    }

    /**
     * 设置腾讯云 SecretId。
     *
     * @param secretId SecretId
     */
    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    /**
     * 获取腾讯云 SecretKey。
     *
     * @return SecretKey
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * 设置腾讯云 SecretKey。
     *
     * @param secretKey SecretKey
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 获取临时会话令牌。
     *
     * @return 会话令牌；未配置时为 {@code null}
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * 设置临时会话令牌。
     *
     * @param sessionToken 临时会话令牌
     */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }
}
