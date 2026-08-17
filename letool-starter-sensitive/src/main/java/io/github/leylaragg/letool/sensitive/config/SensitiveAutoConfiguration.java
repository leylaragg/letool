package io.github.leylaragg.letool.sensitive.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leylaragg.letool.sensitive.core.SensitiveProcessor;
import io.github.leylaragg.letool.sensitive.core.SensitiveStrategyRegistry;
import io.github.leylaragg.letool.sensitive.jackson.SensitiveModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 脱敏模块自动配置。
 *
 * <p>默认提供不可变策略注册表、脱敏处理器和字段级 Jackson 模块。
 * 用户声明同类型 Bean 时，Starter 默认实现会自动退让。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(SensitiveProperties.class)
@ConditionalOnProperty(
        prefix = "letool.sensitive",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SensitiveAutoConfiguration {

    /**
     * 创建包含全部内置策略的默认注册表。
     *
     * @return 不可变策略注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveStrategyRegistry sensitiveStrategyRegistry() {
        return SensitiveStrategyRegistry.defaults();
    }

    /**
     * 创建使用当前策略注册表的脱敏处理器。
     *
     * @param registry 策略注册表
     * @return 脱敏处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveProcessor sensitiveProcessor(SensitiveStrategyRegistry registry) {
        return new SensitiveProcessor(registry);
    }

    /**
     * 创建字段级 Jackson 脱敏模块。
     *
     * @param processor 脱敏处理器
     * @return Jackson 脱敏模块
     */
    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.sensitive.jackson",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public SensitiveModule sensitiveModule(SensitiveProcessor processor) {
        return new SensitiveModule(processor);
    }
}
