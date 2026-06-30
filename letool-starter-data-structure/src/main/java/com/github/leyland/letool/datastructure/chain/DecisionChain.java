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

    private final List<DecisionRule<T, R>> rules;

    DecisionChain(List<DecisionRule<T, R>> rules) {
        this.rules = new ArrayList<>(rules);
    }

    /**
     * 执行决策链，按注册顺序依次评估条件，首个匹配即执行对应动作并返回结果.
     *
     * @param context 上下文对象
     * @return 匹配规则的动作返回值，若无匹配且未设置 otherwise 则返回 {@code null}
     * @throws IllegalStateException 如果没有匹配的规则且未设置 {@code otherwise}
     */
    public R execute(T context) {
        for (DecisionRule<T, R> rule : rules) {
            if (rule.matches(context)) {
                return rule.execute(context);
            }
        }
        return null;
    }

    /** 创建构建器. */
    public static <T, R> DecisionChainBuilder<T, R> builder() {
        return new DecisionChainBuilder<>();
    }

    /**
     * 创建仅包含一条无条件规则的决策链（等价于一个简单的 Function）.
     */
    public static <T, R> DecisionChain<T, R> of(Function<T, R> action) {
        return DecisionChain.<T, R>builder().otherwise(action).build();
    }

    // ---- 内部类 ----

    public static class DecisionRule<T, R> {
        private final Predicate<T> condition;
        private final Function<T, R> action;
        private final boolean isDefault;

        DecisionRule(Predicate<T> condition, Function<T, R> action, boolean isDefault) {
            this.condition = condition;
            this.action = Objects.requireNonNull(action, "action must not be null");
            this.isDefault = isDefault;
        }

        boolean matches(T context) {
            return isDefault || (condition != null && condition.test(context));
        }

        R execute(T context) {
            return action.apply(context);
        }
    }
}
