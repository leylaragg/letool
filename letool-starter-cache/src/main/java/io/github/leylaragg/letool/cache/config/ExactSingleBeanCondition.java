package io.github.leylaragg.letool.cache.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

import java.util.Objects;

/**
 * 严格按 Bean 数量判断唯一候选，不把多 Bean 中的 {@code @Primary} 当成单数据源。
 */
abstract class ExactSingleBeanCondition implements Condition {

    private final String beanTypeName;

    /**
     * 记录必选依赖中的 Bean 类型。
     *
     * @param beanType Bean 类型
     */
    protected ExactSingleBeanCondition(Class<?> beanType) {
        this(Objects.requireNonNull(beanType, "Bean 类型不能为空").getName());
    }

    /**
     * 延迟解析可选依赖中的 Bean 类型，避免业务项目未引入该依赖时提前加载失败。
     *
     * @param beanTypeName Bean 类型全限定名
     */
    protected ExactSingleBeanCondition(String beanTypeName) {
        this.beanTypeName = Objects.requireNonNull(beanTypeName, "Bean 类型名称不能为空");
    }

    /**
     * 仅在目标类型存在且容器中恰好有一个候选时匹配。
     *
     * @param context 当前条件上下文
     * @param metadata 条件所在元素的元数据
     * @return 是否满足唯一候选约束
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (context.getBeanFactory() == null) {
            return false;
        }
        ClassLoader classLoader = context.getClassLoader();
        if (!ClassUtils.isPresent(beanTypeName, classLoader)) {
            return false;
        }
        Class<?> beanType = ClassUtils.resolveClassName(beanTypeName, classLoader);
        return context.getBeanFactory().getBeanNamesForType(beanType, false, false).length == 1;
    }
}
