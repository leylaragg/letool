package com.github.leyland.letool.pay.config;

import com.github.leyland.letool.pay.core.PayProvider;
import com.github.leyland.letool.pay.core.PayTemplate;
import com.github.leyland.letool.pay.model.PayChannel;
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
import java.util.Map;

/**
 * 支付模块自动配置类，负责根据 {@link PayProperties} 中的配置创建所有支付相关的 Bean。
 *
 * <p><b>自动创建的 Bean：</b></p>
 * <ul>
 *   <li>{@code payTemplate} — 支付模板，支付模块的统一入口（必创建）</li>
 *   <li>{@code alipayProvider} — 支付宝支付提供者</li>
 *   <li>{@code wechatPayProvider} — 微信支付提供者</li>
 *   <li>{@code mockPayProvider} — Mock 支付提供者（开发/测试用，始终创建）</li>
 * </ul>
 *
 * <p>通过 {@code letool.pay.enabled=false} 可禁用整个模块。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(PayProperties.class)
@ConditionalOnProperty(prefix = "letool.pay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PayAutoConfiguration {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(PayAutoConfiguration.class);

    // ======================== 支付提供者 Bean ========================

    /**
     * 创建支付宝支付提供者 Bean。
     *
     * <p>当项目中已存在同名 Bean 时不会覆盖。支付宝的配置来源于
     * {@link PayProperties#getAlipay()}。</p>
     *
     * @param properties 支付模块配置属性
     * @return AlipayProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AlipayProvider alipayProvider(PayProperties properties) {
        log.info("Registering AlipayProvider");
        return new AlipayProvider(properties.getAlipay());
    }

    /**
     * 创建微信支付提供者 Bean。
     *
     * <p>当项目中已存在同名 Bean 时不会覆盖。微信支付的配置来源于
     * {@link PayProperties#getWechat()}。</p>
     *
     * @param properties 支付模块配置属性
     * @return WechatPayProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public WechatPayProvider wechatPayProvider(PayProperties properties) {
        log.info("Registering WechatPayProvider");
        return new WechatPayProvider(properties.getWechat());
    }

    /**
     * 创建 Mock 支付提供者 Bean。
     *
     * <p>Mock 提供者始终注册，返回模拟的成功结果，适用于开发和测试环境。
     * 当项目中已存在同名 Bean 时不会覆盖。</p>
     *
     * @return MockPayProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MockPayProvider mockPayProvider() {
        log.info("Registering MockPayProvider");
        return new MockPayProvider();
    }

    // ======================== 支付模板 Bean ========================

    /**
     * 创建支付模板 Bean。
     *
     * <p>将所有已注册的 {@link PayProvider} 实例收集到一个 Map 中，
     * 并以此构建 {@link PayTemplate}。支持通过 {@code PayChannel} 枚举值
     * 路由到对应的提供者。</p>
     *
     * <p>当项目中已存在同名 Bean 时不会覆盖，便于高级用户自定义扩展。</p>
     *
     * @param alipayProvider   支付宝提供者
     * @param wechatProvider   微信支付提供者
     * @param mockPayProvider  Mock 提供者
     * @param properties       支付模块配置属性
     * @return PayTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public PayTemplate payTemplate(AlipayProvider alipayProvider,
                                   WechatPayProvider wechatProvider,
                                   MockPayProvider mockPayProvider,
                                   PayProperties properties) {
        log.info("Building PayTemplate with providers: ALIPAY, WECHAT, MOCK");
        Map<String, PayProvider> providers = new HashMap<>();
        providers.put(PayChannel.ALIPAY.name(), alipayProvider);
        providers.put(PayChannel.WECHAT.name(), wechatProvider);
        providers.put(PayChannel.MOCK.name(), mockPayProvider);
        return new PayTemplate(providers, properties);
    }
}
