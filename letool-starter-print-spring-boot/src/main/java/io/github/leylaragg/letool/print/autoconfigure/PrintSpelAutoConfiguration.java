package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.print.spel.RestrictedSpelConditionExpression;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据额外模块是否存在和显式开关装配受限 SpEL。
 *
 * @author leyland
 */
@AutoConfiguration(before = PrintAutoConfiguration.class)
@ConditionalOnProperty(
        prefix = "letool.print",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PrintSpelAutoConfiguration {

    /** 额外模块存在时才加载包含具体 SpEL 类型的方法签名。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestrictedSpelConditionExpression.class)
    @ConditionalOnProperty(
            prefix = "letool.print.spel",
            name = "enabled",
            havingValue = "true")
    static class AvailableSpelConfiguration {

        /** @return 使用框架固定安全预算的受限 SpEL 提供方 */
        @Bean
        @ConditionalOnMissingBean(RestrictedSpelConditionExpression.class)
        RestrictedSpelConditionExpression restrictedSpelConditionExpression() {
            return new RestrictedSpelConditionExpression();
        }
    }

    /** 显式开启但额外模块缺失时，把配置错误留在启动阶段。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass(
            "io.github.leylaragg.letool.print.spel.RestrictedSpelConditionExpression")
    @ConditionalOnProperty(
            prefix = "letool.print.spel",
            name = "enabled",
            havingValue = "true")
    static class MissingSpelConfiguration {

        /**
         * @return 不会实际创建的模块要求标记
         * @throws IllegalStateException SpEL 开启但额外模块缺失时始终抛出
         */
        @Bean("printSpelModuleRequirement")
        Object printSpelModuleRequirement() {
            throw new IllegalStateException(
                    "启用打印 SpEL 需要显式引入 letool-starter-print-expression-spel");
        }
    }
}
