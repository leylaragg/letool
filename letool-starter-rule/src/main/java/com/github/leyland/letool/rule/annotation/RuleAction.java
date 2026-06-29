package com.github.leyland.letool.rule.annotation;

import java.lang.annotation.*;

/**
 * 规则动作注解 —— 标记 {@link com.github.leyland.letool.rule.component.NodeComponent NodeComponent}
 * 中的方法为节点的主执行方法.
 *
 * <h3>设计意图</h3>
 * <p>当 {@link com.github.leyland.letool.rule.component.NodeComponent#process NodeComponent.process()}
 * 方法不足以表达业务语义时，可通过此注解标记一个更具语义的业务方法作为节点的入口.
 * 规则引擎在节点初始化时发现此注解并优先调用.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @RuleComponent("scoreCalculator")
 * public class ScoreCalculator extends NodeComponent {
 *
 *     @Override
 *     public void process(RuleContext context) {
 *         // 默认执行逻辑
 *     }
 *
 *     @RuleAction
 *     public void calculateScore(RuleContext context) {
 *         // 更具体的业务逻辑入口
 *     }
 * }
 * }</pre>
 *
 * <p>注意：被标记的方法必须接收 {@link com.github.leyland.letool.rule.context.RuleContext RuleContext}
 * 参数，返回类型为 void.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.rule.annotation.RuleComponent
 * @see com.github.leyland.letool.rule.component.NodeComponent
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RuleAction {
}
