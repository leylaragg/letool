package com.github.leyland.letool.datastructure.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 决策链 —— 消除深层 if-else，将条件判断与执行逻辑组成有序链，按注册顺序匹配，首个命中即执行并返回.
 *
 * <p>使用场景：订单路由、审批流程、规则匹配等复杂的多层 if-else 分支逻辑.</p>
 *
 * <pre>{@code
 * DecisionChain<Order, String> chain = DecisionChain.<Order, String>builder()
 *     .when(o -> o.getAmount() > 10000, o -> "大额订单，走风控流程")
 *     .when(o -> o.isVip(),          o -> "VIP客户，走优先通道")
 *     .when(o -> o.getType() == 1,   o -> "普通订单")
 *     .when(o -> o.getSource() > 5,  o -> "渠道订单")
 *     .otherwise(o -> "默认流程");
 *
 * String result = chain.execute(order);
 * }</pre>
 *
 * @param <T> 上下文类型（输入）
 * @param <R> 结果类型（输出）
 * @author leyland
 * @since 2.0.0
 */
public class DecisionChain<T, R> {

    /**
     * 构建完成时复制得到的有序规则快照。
     */
    private final List<DecisionRule<T, R>> rules;

    /**
     * 使用构建器中已经校验过的规则创建决策链。
     *
     * @param rules 按匹配优先级排列的规则集合
     */
    DecisionChain(List<DecisionRule<T, R>> rules) {
        // 与构建器的可变列表隔离，避免 build 后继续修改构建器影响已创建的决策链。
        this.rules = new ArrayList<>(rules);
    }

    /**
     * 执行决策链，按注册顺序依次评估条件，首个匹配即执行对应动作并返回结果.
     *
     * @param context 上下文对象
     * @return 首个匹配规则的动作返回值
     * @throws IllegalStateException 如果没有匹配的规则且未设置 {@code otherwise}
     */
    public R execute(T context) {
        for (DecisionRule<T, R> rule : rules) {
            // 决策链采用首个命中即返回的语义，后续规则不会继续执行。
            if (rule.matches(context)) {
                return rule.execute(context);
            }
        }
        // 静默返回 null 容易掩盖漏配 otherwise，因此无匹配时明确失败。
        throw new IllegalStateException("No matching rule found in decision chain for context: " + context);
    }

    /**
     * 创建一个尚未注册规则的构建器。
     *
     * @param <T> 上下文类型
     * @param <R> 结果类型
     * @return 新的决策链构建器
     */
    public static <T, R> DecisionChainBuilder<T, R> builder() {
        return new DecisionChainBuilder<>();
    }

    /**
     * 创建仅包含一条无条件规则的决策链（等价于一个简单的 Function）.
     *
     * @param action 接收上下文并返回决策结果的无条件动作
     * @param <T> 上下文类型
     * @param <R> 结果类型
     * @return 只包含默认动作的决策链
     * @throws NullPointerException 当 {@code action} 为 {@code null}
     */
    public static <T, R> DecisionChain<T, R> of(Function<T, R> action) {
        return DecisionChain.<T, R>builder().otherwise(action).build();
    }

    // ---- 内部类 ----

    /**
     * 决策链内部的一条不可变规则。
     *
     * @param <T> 上下文类型
     * @param <R> 结果类型
     */
    public static class DecisionRule<T, R> {

        /**
         * 普通规则的匹配条件；默认规则没有条件，因此允许为 {@code null}。
         */
        private final Predicate<T> condition;

        /**
         * 规则命中后执行的动作。
         */
        private final Function<T, R> action;

        /**
         * 是否为始终命中的默认规则。
         */
        private final boolean isDefault;

        /**
         * 创建一条决策规则。
         *
         * @param condition 普通规则的匹配条件；默认规则允许为 {@code null}
         * @param action 规则命中后执行的动作，不允许为 {@code null}
         * @param isDefault 是否为默认规则
         * @throws NullPointerException 当 {@code action} 为 {@code null}
         */
        DecisionRule(Predicate<T> condition, Function<T, R> action, boolean isDefault) {
            this.condition = condition;
            this.action = Objects.requireNonNull(action, "action must not be null");
            this.isDefault = isDefault;
        }

        /**
         * 判断规则是否匹配当前上下文。
         *
         * @param context 当前决策上下文
         * @return 默认规则或条件返回 {@code true} 时返回 {@code true}
         */
        boolean matches(T context) {
            return isDefault || (condition != null && condition.test(context));
        }

        /**
         * 执行规则动作。
         *
         * @param context 当前决策上下文
         * @return 动作计算得到的决策结果
         */
        R execute(T context) {
            return action.apply(context);
        }
    }
}
