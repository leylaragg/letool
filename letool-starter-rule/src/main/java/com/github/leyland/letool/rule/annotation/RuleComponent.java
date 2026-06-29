package com.github.leyland.letool.rule.annotation;

import java.lang.annotation.*;

/**
 * 规则组件注解 —— 标记一个类为规则引擎的可执行节点组件.
 *
 * <h3>设计意图</h3>
 * <p>规则引擎通过扫描标注了此注解的类，自动发现和注册规则节点。每个规则节点
 * 对应规则链中的一个执行步骤，节点之间通过链定义（YAML/JSON）编排执行顺序.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @RuleComponent("ageValidator")
 * public class AgeValidator extends NodeComponent {
 *     @Override
 *     public void process(RuleContext context) {
 *         Integer age = context.getParam("age");
 *         if (age < 18) {
 *             throw new RuleException("AGE_001", "年龄不满足要求");
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>注册机制</h3>
 * <p>标注此注解且继承 {@link com.github.leyland.letool.rule.component.NodeComponent NodeComponent}
 * 的类会被 {@link com.github.leyland.letool.rule.config.RuleAutoConfiguration RuleAutoConfiguration}
 * 自动扫描并注册到规则引擎中.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.rule.component.NodeComponent
 * @see com.github.leyland.letool.rule.annotation.RuleCondition
 * @see com.github.leyland.letool.rule.annotation.RuleAction
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RuleComponent {

    /**
     * 组件名称，在规则链中通过此名称引用该节点.
     *
     * <p>名称必须全局唯一，规则链 YAML/JSON 定义中的节点名称需与此处一致.</p>
     *
     * @return 组件名称
     */
    String value();

    /**
     * 组件描述信息，用于文档和监控面板中展示.
     *
     * @return 组件描述，默认为空字符串
     */
    String description() default "";
}
