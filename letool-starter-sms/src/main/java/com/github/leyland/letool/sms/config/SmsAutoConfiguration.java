package com.github.leyland.letool.sms.config;

import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.core.SmsTemplate;
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
 *   <li>当 {@code letool.sms.enabled=true} 时生效（该属性默认为 {@code true}，即引入依赖后自动启用）。</li>
 *   <li>可通过设置 {@code letool.sms.enabled=false} 显式禁用整个短信模块。</li>
 * </ul>
 *
 * <h3>Provider 选择策略</h3>
 * <p>根据 {@code letool.sms.default-provider} 的值（默认为 {@code "aliyun"}）选择对应的 Provider：</p>
 * <ul>
 *   <li><b>aliyun</b> — 注册 {@link AliyunSmsProvider}</li>
 *   <li><b>tencent</b> — 注册 {@link TencentSmsProvider}</li>
 *   <li><b>其他</b> — 注册 {@link MockSmsProvider} 作为默认后备</li>
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
@ConditionalOnProperty(prefix = "letool.sms", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SmsAutoConfiguration {

    // ======================== 日志记录器 ========================

    private static final Logger log = LoggerFactory.getLogger(SmsAutoConfiguration.class);

    // ======================== Bean 定义：短信提供者 ========================

    /**
     * 根据配置创建对应的短信服务提供者 Bean。
     *
     * <p>根据 {@code properties.getDefaultProvider()} 的值选择注册：
     * {@code "aliyun"}、{@code "tencent"} 或 Mock 默认实现。</p>
     *
     * @param properties 短信配置属性（由 Spring Boot 自动绑定）
     * @return 短信服务提供者实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SmsProvider smsProvider(SmsProperties properties) {
        String provider = properties.getDefaultProvider();
        log.info("Initializing SMS provider: {}", provider);

        switch (provider.toLowerCase()) {
            case "aliyun":
                log.info("Registering AliyunSmsProvider");
                return new AliyunSmsProvider(properties.getAliyun());
            case "tencent":
                log.info("Registering TencentSmsProvider");
                return new TencentSmsProvider(properties.getTencent());
            default:
                log.warn("Unknown SMS provider '{}', falling back to MockSmsProvider", provider);
                return new MockSmsProvider();
        }
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
