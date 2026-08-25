package io.github.leylaragg.letool.ruleengine.autoconfigure;

import io.github.leylaragg.letool.exception.config.ExceptionAutoConfiguration;
import io.github.leylaragg.letool.exception.message.MessageBundleContributor;
import io.github.leylaragg.letool.exception.message.MessageResolver;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngineBuilder;
import io.github.leylaragg.letool.ruleengine.diagnostic.ChineseDiagnosticMessageResolver;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticMessageResolver;
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import io.github.leylaragg.letool.ruleengine.function.RuleFunctionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 提供完整规则引擎快照，并收集应用声明的规则函数。 */
@AutoConfiguration(after = ExceptionAutoConfiguration.class)
@ConditionalOnClass(ExpressionEngine.class)
@ConditionalOnProperty(
        prefix = "letool.rule-engine",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(RuleEngineProperties.class)
public class RuleEngineAutoConfiguration {

    /** @return 规则引擎消息资源贡献 */
    @Bean(name = "ruleEngineMessageBundle")
    public MessageBundleContributor ruleEngineMessageBundle() {
        return MessageBundleContributor.of("i18n/letool-rule-engine/messages");
    }

    /**
     * 创建诊断文案解析器；异常模块关闭时回退到规则引擎内置中文文案。
     *
     * @param messageResolvers 可选的通用消息解析器
     * @return 诊断文案解析器
     */
    @Bean
    @ConditionalOnMissingBean(DiagnosticMessageResolver.class)
    public DiagnosticMessageResolver diagnosticMessageResolver(
            ObjectProvider<MessageResolver> messageResolvers) {
        MessageResolver messageResolver = messageResolvers.getIfAvailable();
        return messageResolver == null
                ? new ChineseDiagnosticMessageResolver()
                : new MessageResolverDiagnosticAdapter(messageResolver);
    }

    /**
     * 使用绑定限制和按 Spring 顺序收集的函数构建完整引擎快照。
     *
     * @param properties 规则引擎配置
     * @param functions 应用声明的共享函数
     * @param factories 应用声明的调用级函数工厂
     * @return 不可变表达式引擎
     */
    @Bean
    @ConditionalOnMissingBean(ExpressionEngine.class)
    public ExpressionEngine expressionEngine(
            RuleEngineProperties properties,
            ObjectProvider<RuleFunction> functions,
            ObjectProvider<RuleFunctionFactory> factories) {
        ExpressionEngineBuilder builder = ExpressionEngine.builder()
                .limits(properties.getLimits().toEngineLimits());
        functions.orderedStream().forEach(builder::registerFunction);
        factories.orderedStream().forEach(builder::registerFunction);
        return builder.build();
    }
}
