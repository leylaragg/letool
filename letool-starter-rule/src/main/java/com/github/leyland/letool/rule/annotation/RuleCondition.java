package com.github.leyland.letool.rule.annotation;

import java.lang.annotation.*;

/**
 * 规则条件注解 —— 标记 {@link com.github.leyland.letool.rule.component.NodeComponent NodeComponent}
 * 中的方法为条件判断方法，用于 IF 类型节点的条件评估.
 *
 * <h3>设计意图</h3>
 * <p>在规则链中，IF 节点需要判断是否执行某个分支。被此注解标记的方法将被规则引擎
 * 调用，根据其返回值（boolean）决定分支走向.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @RuleComponent("fraudCheck")
 * public class FraudCheckNode extends NodeComponent {
 *
 *     @Override
 *     public void process(RuleContext context) {
 *         // 执行反欺诈检查逻辑
 *     }
 *
 *     @RuleCondition
 *     public boolean shouldCheckFraud(RuleContext context) {
 *         BigDecimal amount = context.getParam("amount");
 *         return amount != null && amount.compareTo(new BigDecimal("10000")) > 0;
 *     }
 * }
 * }</pre>
 *
 * <p>注意：被标记的方法必须接收 {@link com.github.leyland.letool.rule.context.RuleContext RuleContext}
 * 参数并返回 boolean 类型.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.rule.annotation.RuleComponent
 * @see com.github.leyland.letool.rule.component.NodeComponent
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RuleCondition {
}
