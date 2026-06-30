package com.github.leyland.letool.datastructure.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@link DecisionChain} 的构建器 —— 链式注册条件规则和默认兜底策略.
 *
 * @param <T> 上下文类型
 * @param <R> 结果类型
 * @author leyland
 * @since 2.0.0
 */
public class DecisionChainBuilder<T, R> {

    private final List<DecisionChain.DecisionRule<T, R>> rules = new ArrayList<>();
    private boolean defaultSet;

    /**
     * 添加一条 if 规则 —— 当 condition 为 true 时执行 action.
     * 规则按添加顺序依次评估，不支持插队.
     */
    public DecisionChainBuilder<T, R> when(Predicate<T> condition, Function<T, R> action) {
        rules.add(new DecisionChain.DecisionRule<>(condition, action, false));
        return this;
    }

    /**
     * 添加兜底规则 —— 当所有 {@link #when} 都未命中时执行.
     * 只能调用一次，重复调用会抛异常.
     */
    public DecisionChainBuilder<T, R> otherwise(Function<T, R> action) {
        if (defaultSet) {
            throw new IllegalStateException("otherwise already set");
        }
        rules.add(new DecisionChain.DecisionRule<>(null, action, true));
        defaultSet = true;
        return this;
    }

    /** 构建决策链. */
    public DecisionChain<T, R> build() {
        if (rules.isEmpty()) {
            throw new IllegalStateException("At least one rule (when or otherwise) required");
        }
        return new DecisionChain<>(rules);
    }
}
