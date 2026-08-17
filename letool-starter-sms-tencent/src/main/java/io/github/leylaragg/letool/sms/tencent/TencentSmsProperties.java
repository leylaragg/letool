package io.github.leylaragg.letool.sms.tencent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云短信配置，绑定 {@code letool.sms.tencent}。
 */
@ConfigurationProperties(prefix = "letool.sms.tencent")
public class TencentSmsProperties {

    private boolean enabled = true;
    private String secretId;
    private String secretKey;
    private String region = "ap-guangzhou";
    private String endpoint = "sms.tencentcloudapi.com";
    private String sdkAppId;
    private String signName;
    private String defaultCountryCode = "86";
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 10;
    private int writeTimeoutSeconds = 10;

    /**
     * 判断腾讯云 Provider 是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置腾讯云 Provider 开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取访问密钥 ID。
     *
     * @return 访问密钥 ID
     */
    public String getSecretId() {
        return secretId;
    }

    /**
     * 设置访问密钥 ID。
     *
     * @param secretId 访问密钥 ID
     */
    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    /**
     * 获取访问密钥。
     *
     * @return 访问密钥
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * 设置访问密钥。
     *
     * @param secretKey 访问密钥
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 获取短信服务地域。
     *
     * @return 短信服务地域
     */
    public String getRegion() {
        return region;
    }

    /**
     * 设置短信服务地域。
     *
     * @param region 短信服务地域
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * 获取短信服务访问地址。
     *
     * @return 短信服务访问地址
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置短信服务访问地址。
     *
     * @param endpoint 短信服务访问地址
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 获取短信应用标识。
     *
     * @return 短信应用标识
     */
    public String getSdkAppId() {
        return sdkAppId;
    }

    /**
     * 设置短信应用标识。
     *
     * @param sdkAppId 短信应用标识
     */
    public void setSdkAppId(String sdkAppId) {
        this.sdkAppId = sdkAppId;
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

    /**
     * 获取无国家码手机号使用的默认国家码。
     *
     * @return 默认国家码
     */
    public String getDefaultCountryCode() {
        return defaultCountryCode;
    }

    /**
     * 设置无国家码手机号使用的默认国家码。
     *
     * @param defaultCountryCode 默认国家码
     */
    public void setDefaultCountryCode(String defaultCountryCode) {
        this.defaultCountryCode = defaultCountryCode;
    }

    /**
     * 获取连接超时秒数。
     *
     * @return 连接超时秒数
     */
    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /**
     * 设置连接超时秒数。
     *
     * @param connectTimeoutSeconds 连接超时秒数
     */
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    /**
     * 获取读取超时秒数。
     *
     * @return 读取超时秒数
     */
    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    /**
     * 设置读取超时秒数。
     *
     * @param readTimeoutSeconds 读取超时秒数
     */
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    /**
     * 获取写入超时秒数。
     *
     * @return 写入超时秒数
     */
    public int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds;
    }

    /**
     * 设置写入超时秒数。
     *
     * @param writeTimeoutSeconds 写入超时秒数
     */
    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) {
        this.writeTimeoutSeconds = writeTimeoutSeconds;
    }
}
