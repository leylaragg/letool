package com.github.leyland.letool.pay.config;

import com.github.leyland.letool.pay.core.PayProvider;
import com.github.leyland.letool.pay.core.PayTemplate;
import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.provider.AlipayProvider;
import com.github.leyland.letool.pay.provider.MockPayProvider;
import com.github.leyland.letool.pay.provider.WechatPayProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 支付模块自动配置类，负责根据 {@link PayProperties} 中的配置创建支付相关 Bean。
 *
 * <p>当前内置的支付宝、微信支付和 Mock provider 均为 stub/mock 实现，不会访问真实支付平台。
 * 为避免模拟支付或模拟验签进入真实资金链路，支付模块默认不启用；启用后如果没有业务项目
 * 自定义 {@link PayProvider}，必须显式设置 {@code letool.pay.stub-enabled=true}
 * 才会创建内置 provider。</p>
 *
 * <p><b>自动创建的 Bean：</b></p>
 * <ul>
 *   <li>{@code payTemplate} — 支付模板，收集所有 {@link PayProvider} Bean 后创建</li>
 *   <li>{@code alipayProvider} — 支付宝 stub provider，仅显式 stub 模式创建</li>
 *   <li>{@code wechatPayProvider} — 微信支付 stub provider，仅显式 stub 模式创建</li>
 *   <li>{@code mockPayProvider} — Mock 支付 provider，仅显式 stub 模式创建</li>
 * </ul>
 *
 * <p>通过 {@code letool.pay.enabled=true} 可显式启用整个模块。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(PayProperties.class)
@ConditionalOnProperty(prefix = "letool.pay", name = "enabled", havingValue = "true")
public class PayAutoConfiguration {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(PayAutoConfiguration.class);

    // ======================== 支付提供者 Bean ========================

    /**
     * 创建支付宝支付提供者 Bean。
     *
     * <p>当前实现为 stub，只有 {@code letool.pay.stub-enabled=true} 时才会创建。
     * 当项目中已存在 {@link AlipayProvider} Bean 时不会覆盖。</p>
     *
     * @param properties 支付模块配置属性
     * @return AlipayProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.pay", name = "stub-enabled", havingValue = "true")
    public AlipayProvider alipayProvider(PayProperties properties) {
        log.warn("Registering AlipayProvider in STUB mode; no real Alipay request will be sent");
        return new AlipayProvider(properties.getAlipay());
    }

    /**
     * 创建微信支付提供者 Bean。
     *
     * <p>当前实现为 stub，只有 {@code letool.pay.stub-enabled=true} 时才会创建。
     * 当项目中已存在 {@link WechatPayProvider} Bean 时不会覆盖。</p>
     *
     * @param properties 支付模块配置属性
     * @return WechatPayProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.pay", name = "stub-enabled", havingValue = "true")
    public WechatPayProvider wechatPayProvider(PayProperties properties) {
        log.warn("Registering WechatPayProvider in STUB mode; no real WeChat Pay request will be sent");
        return new WechatPayProvider(properties.getWechat());
    }

    /**
     * 创建 Mock 支付提供者 Bean。
     *
     * <p>当前实现为 mock，只有 {@code letool.pay.stub-enabled=true} 时才会创建。
     * 当项目中已存在 {@link MockPayProvider} Bean 时不会覆盖。</p>
     *
     * @return MockPayProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.pay", name = "stub-enabled", havingValue = "true")
    public MockPayProvider mockPayProvider() {
        log.warn("Registering MockPayProvider in STUB mode; no real payment will be made");
        return new MockPayProvider();
    }

    // ======================== 支付模板 Bean ========================

    /**
     * 创建支付模板 Bean。
     *
     * <p>将所有已注册的 {@link PayProvider} 实例按 {@link PayProvider#getProviderName()}
     * 索引到一个 Map 中，并以此构建 {@link PayTemplate}。如果启用支付模块但没有任何
     * provider，则启动失败，提醒业务项目注册真实 provider 或显式启用 stub 模式。</p>
     *
     * <p>当项目中已存在同名 Bean 时不会覆盖，便于高级用户自定义扩展。</p>
     *
     * @param providerBeans 已注册的支付 provider Bean
     * @param properties    支付模块配置属性
     * @return PayTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public PayTemplate payTemplate(Map<String, PayProvider> providerBeans, PayProperties properties) {
        if (providerBeans.isEmpty()) {
            throw noPayProvider();
        }

        Map<String, PayProvider> providers = new HashMap<>();
        providerBeans.values().forEach(provider ->
                providers.put(providerName(provider), provider));

        log.info("Building PayTemplate with providers: {}", providers.keySet());
        return new PayTemplate(providers, properties);
    }

    /**
     * 构建未注册任何支付 provider 时的配置错误。
     *
     * @return 支付配置异常
     */
    private PayException noPayProvider() {
        return new PayException("[letool-pay] 未注册任何 PayProvider；"
                + "生产接入请在业务项目中注册自定义 PayProvider Bean，"
                + "开发演示请设置 letool.pay.stub-enabled=true。");
    }

    /**
     * 获取并规范化 provider 名称。
     *
     * @param provider 支付 provider
     * @return 大写 provider 名称
     */
    private String providerName(PayProvider provider) {
        String name = provider.getProviderName();
        if (name == null || name.trim().isEmpty()) {
            throw new PayException("[letool-pay] PayProvider.getProviderName() 不能为空。");
        }
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
