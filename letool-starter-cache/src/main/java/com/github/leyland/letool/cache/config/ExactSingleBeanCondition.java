package com.github.leyland.letool.cache.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 严格按 Bean 数量判断唯一候选，不把多 Bean 中的 {@code @Primary} 当成单数据源。
 */
abstract class ExactSingleBeanCondition implements Condition {

    private final Class<?> beanType;

    protected ExactSingleBeanCondition(Class<?> beanType) {
        this.beanType = beanType;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (context.getBeanFactory() == null) {
            return false;
        }
        return context.getBeanFactory().getBeanNamesForType(beanType, false, false).length == 1;
    }
}
