package com.github.leyland.letool.sensitive.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.leyland.letool.sensitive.jackson.SensitiveModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 脱敏自动配置 —— 在 Spring Boot 启动时自动注册 Jackson 脱敏 Module 和日志框架集成.
 *
 * <h3>自动注册的 Bean</h3>
 * <ul>
 *   <li><b>sensitiveModule</b> —— Jackson Module，序列化 String 字段时检查 {@code @Sensitive} 注解并脱敏</li>
 *   <li><b>sensitiveLogInitializer</b> —— 标记 Bean，确保日志层面的脱敏集成状态可被 Spring 管理</li>
 * </ul>
 *
 * <h3>启用条件</h3>
 * <p>受 {@code letool.sensitive.enabled} 控制（默认 true）.
 * Jackson 集成额外受 {@code letool.sensitive.jackson.enabled} 控制.
 * 日志集成额外受 {@code letool.sensitive.log.enabled} 控制.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(SensitiveProperties.class)
@ConditionalOnProperty(prefix = "letool.sensitive", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SensitiveAutoConfiguration {

    /**
     * 注册 SensitiveModule 到 Jackson ObjectMapper —— 使 Controller 返回 JSON 时自动对 @Sensitive 字段脱敏.
     * 仅在 classpath 中存在 Jackson ObjectMapper 时生效.
     */
    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnMissingBean(SensitiveModule.class)
    @ConditionalOnProperty(prefix = "letool.sensitive.jackson", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SensitiveModule sensitiveModule() {
        return new SensitiveModule();
    }

    /**
     * 注册日志脱敏集成标记 Bean —— Logback/Log4j2 通过 SPI 自动发现 %maskedMsg 转换器，
     * 此 Bean 确保日志层面的脱敏集成状态可被 Spring 管理和条件化禁用.
     */
    @Bean
    @ConditionalOnMissingBean(SensitiveLogInitializer.class)
    @ConditionalOnProperty(prefix = "letool.sensitive.log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SensitiveLogInitializer sensitiveLogInitializer() {
        return new SensitiveLogInitializer();
    }

    /**
     * 日志脱敏集成标记 —— 实际脱敏由 Logback SensitiveMessageConverter / Log4j2 SensitivePatternConverter 通过 SPI 实现，
     * 此 Bean 作为 Spring 容器中的哨兵，表示日志脱敏已激活.
     */
    public static class SensitiveLogInitializer {
        public SensitiveLogInitializer() {
            // 占位构造器 —— 实际的 PatternLayout 转换器通过 logback.xml 中的 %maskedMsg 或
            // Log4j2 PatternLayout 中的 %maskedMsg 触发，由对应的 Converter 实现类处理
        }
    }
}
