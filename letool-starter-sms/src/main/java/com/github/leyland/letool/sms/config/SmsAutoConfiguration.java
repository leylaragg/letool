package com.github.leyland.letool.sms.config;

import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.core.SmsTemplate;
import com.github.leyland.letool.sms.exception.SmsException;
import com.github.leyland.letool.sms.provider.AliyunSmsProvider;
import com.github.leyland.letool.sms.provider.MockSmsProvider;
import com.github.leyland.letool.sms.provider.TencentSmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

// ======================== 类级别说明 ========================

/**
 * <p>短信模块的 Spring Boot 自动配置类。</p>
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>根据配置属性自动选择并注册对应的 {@link SmsProvider} Bean。</li>
 *   <li>注册 {@link SmsTemplate} Bean — 用户操作短信模块的核心入口。</li>
 * </ul>
 *
 * <h3>启用条件</h3>
 * <ul>
 *   <li>当 {@code letool.sms.enabled=true} 时生效（该属性默认为 {@code false}，引入依赖后不会自动模拟发送）。</li>
 *   <li>当前内置 provider 均为 mock/stub，只有设置 {@code letool.sms.mock-enabled=true} 才会创建。</li>
 * </ul>
 *
 * <h3>Provider 选择策略</h3>
 * <p>在显式开启 mock 模式后，根据 {@code letool.sms.default-provider} 的值选择对应的模拟 Provider：</p>
 * <ul>
 *   <li><b>aliyun</b> — 注册 {@link AliyunSmsProvider} stub</li>
 *   <li><b>tencent</b> — 注册 {@link TencentSmsProvider} stub</li>
 *   <li><b>mock</b> — 注册 {@link MockSmsProvider}</li>
 * </ul>
 *
 * <h3>Bean 覆盖机制</h3>
 * <ul>
 *   <li>{@link SmsProvider} 标注了 {@code @ConditionalOnMissingBean}，用户可自行提供自定义实现来覆盖默认行为。</li>
 *   <li>{@link SmsTemplate} 同样标注了 {@code @ConditionalOnMissingBean}，用户可自行接管短信操作门面。</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SmsProperties.class)
@ConditionalOnProperty(prefix = "letool.sms", name = "enabled", havingValue = "true")
public class SmsAutoConfiguration {

    // ======================== 日志记录器 ========================

    private static final Logger log = LoggerFactory.getLogger(SmsAutoConfiguration.class);

    // ======================== Bean 定义：短信提供者 ========================

    /**
     * 根据配置创建对应的短信服务提供者 Bean。
     *
     * <p>当前内置 provider 均为 mock/stub，因此只有在
     * {@code letool.sms.mock-enabled=true} 时才会创建。业务项目可以注册自己的
     * {@link SmsProvider} Bean 来接入真实短信 SDK 或 HTTP API。</p>
     *
     * @param properties 短信配置属性（由 Spring Boot 自动绑定）
     * @return 短信服务提供者实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SmsProvider smsProvider(SmsProperties properties) {
        String provider = properties.getDefaultProvider();
        if (!properties.isMockEnabled()) {
            throw mockModeDisabled(provider);
        }

        log.warn("[letool-sms] Initializing {} SMS provider in mock/stub mode; no real SMS will be sent",
                provider);

        switch (provider.toLowerCase()) {
            case "aliyun":
                return new AliyunSmsProvider(properties.getAliyun());
            case "tencent":
                return new TencentSmsProvider(properties.getTencent());
            case "mock":
                return new MockSmsProvider();
            default:
                throw unsupportedProvider(provider);
        }
    }

    /**
     * 构建未显式启用 mock 模式时的配置错误。
     *
     * @param provider 当前配置的短信 provider
     * @return 短信配置异常
     */
    private SmsException mockModeDisabled(String provider) {
        return new SmsException("[letool-sms] " + provider + " 当前为内置 mock/stub provider，未启用 mock 模式；"
                + "如需开发演示请设置 letool.sms.mock-enabled=true，"
                + "如需生产接入请在业务项目中注册自定义 SmsProvider Bean。");
    }

    /**
     * 构建未知短信 provider 的配置错误。
     *
     * @param provider 当前配置的短信 provider
     * @return 短信配置异常
     */
    private SmsException unsupportedProvider(String provider) {
        return new SmsException("[letool-sms] " + provider
                + " 是不支持的短信 provider；可选值为 aliyun、tencent、mock。");
    }

    // ======================== Bean 定义：短信模板 ========================

    /**
     * 创建短信模板 Bean — 用户操作短信模块的核心入口。
     *
     * <p>{@link SmsTemplate} 封装了 {@link SmsProvider} 与频率限制逻辑，
     * 提供 Builder 模式的链式调用 API，简化短信构建与发送流程。</p>
     *
     * @param smsProvider 短信服务提供者
     * @param properties  短信配置属性（用于读取频率限制配置）
     * @return 短信模板实例
     */
    @Bean
    @ConditionalOnMissingBean(SmsTemplate.class)
    public SmsTemplate smsTemplate(SmsProvider smsProvider, SmsProperties properties) {
        return new SmsTemplate(smsProvider, properties);
    }
}
