package com.github.leyland.letool.sms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// ======================== 类级别说明 ========================

/**
 * <p>短信模块的全局配置属性类，绑定 {@code letool.sms} 前缀的配置项。</p>
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>管理短信模块的全局开关 ({@code enabled}) 与默认服务商选择 ({@code defaultProvider})。</li>
 *   <li>提供阿里云 ({@link Aliyun})、腾讯云 ({@link Tencent}) 两大服务商的独立配置内部类。</li>
 *   <li>提供频率限制 ({@link RateLimit}) 配置，防止短信接口被滥用。</li>
 * </ul>
 *
 * <h3>配置示例 (application.yml)</h3>
 * <pre>{@code
 * letool:
 *   sms:
 *     enabled: true
 *     default-provider: aliyun
 *     aliyun:
 *       access-key-id: your-access-key-id
 *       access-key-secret: your-access-key-secret
 *       sign-name: 您的应用
 *       region-id: cn-hangzhou
 *     tencent:
 *       secret-id: your-secret-id
 *       secret-key: your-secret-key
 *       app-id: your-sms-app-id
 *       sign-name: 您的应用
 *     rate-limit:
 *       enabled: true
 *       max-per-minute: 10
 *       max-per-day: 100
 * }</pre>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>{@code defaultProvider} 决定自动配置类将注册哪个服务商的 Provider Bean。</li>
 *   <li>当未配置任何服务商参数时，自动配置类将回退到 Mock 实现。</li>
 *   <li>频率限制基于内存中的 {@code ConcurrentHashMap} 实现，重启后计数清零。</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.sms")
public class SmsProperties {

    // ======================== 全局配置字段 ========================

    /** 短信模块总开关，默认开启 */
    private boolean enabled = true;

    /** 默认短信服务商：aliyun / tencent / mock，默认 aliyun */
    private String defaultProvider = "aliyun";

    /** 阿里云短信配置 */
    private Aliyun aliyun = new Aliyun();

    /** 腾讯云短信配置 */
    private Tencent tencent = new Tencent();

    /** 频率限制配置 */
    private RateLimit rateLimit = new RateLimit();

    // ======================== Getter / Setter ========================

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDefaultProvider() { return defaultProvider; }

    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }

    public Aliyun getAliyun() { return aliyun; }

    public void setAliyun(Aliyun aliyun) { this.aliyun = aliyun; }

    public Tencent getTencent() { return tencent; }

    public void setTencent(Tencent tencent) { this.tencent = tencent; }

    public RateLimit getRateLimit() { return rateLimit; }

    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    // ======================== 内部类：阿里云配置 ========================

    /**
     * <p>阿里云短信服务的配置项，与 {@code letool.sms.aliyun} 下的属性一一对应。</p>
     *
     * <h3>字段说明</h3>
     * <ul>
     *   <li><b>accessKeyId</b> — 阿里云 AccessKey ID，用于 API 鉴权。</li>
     *   <li><b>accessKeySecret</b> — 阿里云 AccessKey Secret，用于 API 鉴权。</li>
     *   <li><b>signName</b> — 短信签名名称，需在阿里云短信控制台审核通过。</li>
     *   <li><b>regionId</b> — 短信服务所在地域，默认 {@code cn-hangzhou}。</li>
     * </ul>
     */
    public static class Aliyun {

        private String accessKeyId;
        private String accessKeySecret;
        private String signName;
        private String regionId = "cn-hangzhou";

        // ---- Getter / Setter ----
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
        public String getSignName() { return signName; }
        public void setSignName(String signName) { this.signName = signName; }
        public String getRegionId() { return regionId; }
        public void setRegionId(String regionId) { this.regionId = regionId; }
    }

    // ======================== 内部类：腾讯云配置 ========================

    /**
     * <p>腾讯云短信服务的配置项，与 {@code letool.sms.tencent} 下的属性一一对应。</p>
     *
     * <h3>字段说明</h3>
     * <ul>
     *   <li><b>secretId</b> — 腾讯云 SecretId，用于 API 鉴权。</li>
     *   <li><b>secretKey</b> — 腾讯云 SecretKey，用于 API 鉴权。</li>
     *   <li><b>appId</b> — 短信应用 SDK AppID，在腾讯云短信控制台获取。</li>
     *   <li><b>signName</b> — 短信签名内容，需在腾讯云短信控制台审核通过。</li>
     * </ul>
     */
    public static class Tencent {

        private String secretId;
        private String secretKey;
        private String appId;
        private String signName;

        // ---- Getter / Setter ----
        public String getSecretId() { return secretId; }
        public void setSecretId(String secretId) { this.secretId = secretId; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getSignName() { return signName; }
        public void setSignName(String signName) { this.signName = signName; }
    }

    // ======================== 内部类：频率限制配置 ========================

    /**
     * <p>短信发送频率限制配置，与 {@code letool.sms.rate-limit} 下的属性一一对应。</p>
     *
     * <h3>字段说明</h3>
     * <ul>
     *   <li><b>enabled</b> — 是否启用频率限制，默认 {@code true}。</li>
     *   <li><b>maxPerMinute</b> — 同一手机号每分钟最大发送次数，默认 10。</li>
     *   <li><b>maxPerDay</b> — 同一手机号每天最大发送次数，默认 100。</li>
     * </ul>
     *
     * <h3>实现说明</h3>
     * <p>频率限制基于内存 {@code ConcurrentHashMap} 实现，服务重启后计数自动清零。
     * 如需分布式环境下的精确限制，建议结合 Redis 等外部存储实现。</p>
     */
    public static class RateLimit {

        /** 是否启用频率限制 */
        private boolean enabled = true;

        /** 同一手机号每分钟最大发送次数 */
        private int maxPerMinute = 10;

        /** 同一手机号每天最大发送次数 */
        private int maxPerDay = 100;

        // ---- Getter / Setter ----
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxPerMinute() { return maxPerMinute; }
        public void setMaxPerMinute(int maxPerMinute) { this.maxPerMinute = maxPerMinute; }
        public int getMaxPerDay() { return maxPerDay; }
        public void setMaxPerDay(int maxPerDay) { this.maxPerDay = maxPerDay; }
    }
}
