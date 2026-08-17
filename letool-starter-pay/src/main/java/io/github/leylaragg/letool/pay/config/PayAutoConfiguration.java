package io.github.leylaragg.letool.pay.config;

import io.github.leylaragg.letool.pay.core.PayProvider;
import io.github.leylaragg.letool.pay.core.PayTemplate;
import io.github.leylaragg.letool.pay.provider.MockPayProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 支付核心 Spring Boot 自动配置。
 *
 * <p>真实平台 Provider 由对应独立模块注册；核心模块仅提供显式启用的 Mock。
 * 启用支付但没有任何 Provider 时，模板创建会快速失败。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(PayProperties.class)
@ConditionalOnProperty(prefix = "letool.pay", name = "enabled", havingValue = "true")
public class PayAutoConfiguration {

    /**
     * 显式启用时创建开发测试用 Mock Provider。
     *
     * @return Mock Provider
     */
    @Bean
    @ConditionalOnMissingBean(name = "mockPayProvider")
    @ConditionalOnProperty(prefix = "letool.pay.mock", name = "enabled", havingValue = "true")
    public MockPayProvider mockPayProvider() {
        return new MockPayProvider();
    }

    /**
     * 创建统一支付操作模板。
     *
     * @param payProviders 容器中的全部支付 Provider
     * @param properties 支付核心配置
     * @return 支付模板
     */
    @Bean
    @ConditionalOnMissingBean(PayTemplate.class)
    public PayTemplate payTemplate(
            ObjectProvider<PayProvider> payProviders,
            PayProperties properties) {
        List<PayProvider> providers = payProviders.orderedStream().toList();
        return new PayTemplate(providers, properties);
    }
}
