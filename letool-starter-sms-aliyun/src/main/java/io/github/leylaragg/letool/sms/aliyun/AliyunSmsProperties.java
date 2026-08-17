package io.github.leylaragg.letool.sms.aliyun;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云短信配置，绑定 {@code letool.sms.aliyun}。
 */
@ConfigurationProperties(prefix = "letool.sms.aliyun")
public class AliyunSmsProperties {

    private boolean enabled = true;
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private String endpoint = "dysmsapi.aliyuncs.com";
    private String regionId = "cn-hangzhou";
    private String signName;

    /**
     * 判断阿里云 Provider 是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置阿里云 Provider 开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 AccessKey ID。
     *
     * @return AccessKey ID
     */
    public String getAccessKeyId() {
        return accessKeyId;
    }

    /**
     * 设置 AccessKey ID。
     *
     * @param accessKeyId AccessKey ID
     */
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    /**
     * 获取 AccessKey Secret。
     *
     * @return AccessKey Secret
     */
    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    /**
     * 设置 AccessKey Secret。
     *
     * @param accessKeySecret AccessKey Secret
     */
    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    /**
     * 获取 STS SecurityToken。
     *
     * @return SecurityToken
     */
    public String getSecurityToken() {
        return securityToken;
    }

    /**
     * 设置 STS SecurityToken。
     *
     * @param securityToken SecurityToken
     */
    public void setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
    }

    /**
     * 获取短信 API Endpoint。
     *
     * @return Endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置短信 API Endpoint。
     *
     * @param endpoint Endpoint
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 获取地域标识。
     *
     * @return 地域标识
     */
    public String getRegionId() {
        return regionId;
    }

    /**
     * 设置地域标识。
     *
     * @param regionId 地域标识
     */
    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    /**
     * 获取默认短信签名。
     *
     * @return 默认短信签名
     */
    public String getSignName() {
        return signName;
    }

    /**
     * 设置默认短信签名。
     *
     * @param signName 默认短信签名
     */
    public void setSignName(String signName) {
        this.signName = signName;
    }
}
