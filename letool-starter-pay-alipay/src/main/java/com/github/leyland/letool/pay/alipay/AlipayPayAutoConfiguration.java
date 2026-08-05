package com.github.leyland.letool.pay.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.github.leyland.letool.pay.config.PayAutoConfiguration;
import com.github.leyland.letool.pay.exception.PayErrorCode;
import com.github.leyland.letool.pay.exception.PayException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 支付宝官方 SDK Provider 自动配置。
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration(before = PayAutoConfiguration.class)
@EnableConfigurationProperties(AlipayPayProperties.class)
@ConditionalOnProperty(prefix = "letool.pay.alipay", name = "enabled", havingValue = "true")
public class AlipayPayAutoConfiguration {

    /**
     * 创建支付宝官方客户端。
     *
     * @param properties 支付宝配置
     * @return 支付宝官方客户端
     */
    @Bean
    @ConditionalOnMissingBean(AlipayClient.class)
    public AlipayClient alipayClient(AlipayPayProperties properties) {
        validate(properties);
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.getGatewayUrl());
        config.setAppId(properties.getAppId());
        config.setPrivateKey(properties.getPrivateKey());
        config.setAlipayPublicKey(properties.getAlipayPublicKey());
        config.setFormat(properties.getFormat());
        config.setCharset(properties.getCharset());
        config.setSignType(properties.getSignType());
        config.setConnectTimeout(properties.getConnectTimeout());
        config.setReadTimeout(properties.getReadTimeout());
        try {
            return new DefaultAlipayClient(config);
        } catch (AlipayApiException exception) {
            throw PayException.causedBy(PayErrorCode.CONFIGURATION_INVALID,
                    exception, "支付宝官方客户端初始化失败");
        }
    }

    /**
     * 创建支付宝支付 Provider。
     *
     * @param client 支付宝官方客户端
     * @param properties 支付宝配置
     * @return 支付宝支付 Provider
     */
    @Bean
    @ConditionalOnMissingBean(AlipayPayProvider.class)
    public AlipayPayProvider alipayPayProvider(
            AlipayClient client,
            AlipayPayProperties properties) {
        validate(properties);
        return new AlipayPayProvider(client, properties);
    }

    private void validate(AlipayPayProperties properties) {
        requireText(properties.getAppId(), "letool.pay.alipay.app-id");
        requireText(properties.getPrivateKey(), "letool.pay.alipay.private-key");
        requireText(properties.getAlipayPublicKey(), "letool.pay.alipay.alipay-public-key");
        requireText(properties.getGatewayUrl(), "letool.pay.alipay.gateway-url");
        requireText(properties.getCharset(), "letool.pay.alipay.charset");
        requireText(properties.getSignType(), "letool.pay.alipay.sign-type");
        if (properties.getConnectTimeout() <= 0 || properties.getReadTimeout() <= 0) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "支付宝连接和读取超时时间必须大于 0");
        }
    }

    private void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    propertyName + " 不能为空");
        }
    }
}
