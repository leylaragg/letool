package com.github.leyland.letool.pay.wechat;

import com.github.leyland.letool.pay.config.PayAutoConfiguration;
import com.github.leyland.letool.pay.exception.PayErrorCode;
import com.github.leyland.letool.pay.exception.PayException;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 微信支付 V3 官方 SDK Provider 自动配置。
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration(before = PayAutoConfiguration.class)
@EnableConfigurationProperties(WechatPayProperties.class)
@ConditionalOnProperty(prefix = "letool.pay.wechat", name = "enabled", havingValue = "true")
public class WechatPayAutoConfiguration {

    /**
     * 创建微信支付官方自动证书配置。
     *
     * <p>官方 SDK 负责请求签名、平台证书下载轮换、响应验签和通知解密。</p>
     *
     * @param properties 微信支付配置
     * @return 官方自动证书配置
     */
    @Bean
    @ConditionalOnMissingBean({RSAAutoCertificateConfig.class, WechatPaySdk.class})
    public RSAAutoCertificateConfig wechatPayConfig(WechatPayProperties properties) {
        validateCredentials(properties);
        try {
            RSAAutoCertificateConfig.Builder builder = new RSAAutoCertificateConfig.Builder()
                    .merchantId(properties.getMchId())
                    .merchantSerialNumber(properties.getMerchantSerialNumber())
                    .apiV3Key(properties.getApiV3Key());
            if (!blank(properties.getPrivateKey())) {
                builder.privateKey(properties.getPrivateKey());
            } else {
                builder.privateKeyFromPath(properties.getPrivateKeyPath());
            }
            return builder.build();
        } catch (RuntimeException exception) {
            throw PayException.causedBy(PayErrorCode.CONFIGURATION_INVALID,
                    exception, "微信支付官方配置初始化失败");
        }
    }

    /**
     * 创建微信支付官方 SDK 调用组合。
     *
     * @param config 官方自动证书配置
     * @return SDK 调用边界
     */
    @Bean
    @ConditionalOnMissingBean(WechatPaySdk.class)
    WechatPaySdk wechatPaySdk(RSAAutoCertificateConfig config) {
        return new OfficialWechatPaySdk(config);
    }

    /**
     * 创建微信支付 Provider。
     *
     * @param sdk SDK 调用边界
     * @param properties 微信支付配置
     * @return 微信支付 Provider
     */
    @Bean
    @ConditionalOnMissingBean(WechatPayProvider.class)
    public WechatPayProvider wechatPayProvider(
            WechatPaySdk sdk,
            WechatPayProperties properties) {
        requireText(properties.getAppId(), "letool.pay.wechat.app-id");
        requireText(properties.getMchId(), "letool.pay.wechat.mch-id");
        requireText(properties.getNotifyUrl(), "letool.pay.wechat.notify-url");
        requireText(properties.getH5Type(), "letool.pay.wechat.h5-type");
        return new WechatPayProvider(sdk, properties);
    }

    private void validateCredentials(WechatPayProperties properties) {
        requireText(properties.getAppId(), "letool.pay.wechat.app-id");
        requireText(properties.getMchId(), "letool.pay.wechat.mch-id");
        requireText(properties.getApiV3Key(), "letool.pay.wechat.api-v3-key");
        requireText(properties.getMerchantSerialNumber(),
                "letool.pay.wechat.merchant-serial-number");
        if (blank(properties.getPrivateKey()) && blank(properties.getPrivateKeyPath())) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "letool.pay.wechat.private-key 和 private-key-path 至少配置一个");
        }
    }

    private void requireText(String value, String propertyName) {
        if (blank(value)) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    propertyName + " 不能为空");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
