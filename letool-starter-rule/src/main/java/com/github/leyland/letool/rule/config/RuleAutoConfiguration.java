package com.github.leyland.letool.rule.config;

import com.github.leyland.letool.rule.core.RuleTemplate;
import com.yomahub.liteflow.core.FlowExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Letool 规则模块自动配置。
 *
 * <p>LiteFlow 负责规则编排、组件管理和规则源加载，本配置仅在 LiteFlow
 * 执行器可用时提供便捷的规则链执行模板。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration(afterName = "com.yomahub.liteflow.springboot.config.LiteflowMainAutoConfiguration")
@ConditionalOnClass(FlowExecutor.class)
public class RuleAutoConfiguration {

    /**
     * 创建规则链执行模板。
     *
     * @param flowExecutor LiteFlow 原生执行器
     * @return 规则链执行模板
     */
    @Bean
    @ConditionalOnBean(FlowExecutor.class)
    @ConditionalOnMissingBean(RuleTemplate.class)
    public RuleTemplate ruleTemplate(FlowExecutor flowExecutor) {
        return new RuleTemplate(flowExecutor);
    }
}
