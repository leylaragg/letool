package com.github.leyland.letool.datastructure.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    /**
     * 按注册顺序保存普通规则和可选的默认规则。
     */
    private final List<DecisionChain.DecisionRule<T, R>> rules = new ArrayList<>();

    /**
     * 标记默认规则是否已经设置，用于阻止重复默认规则和不可达的后续普通规则。
     */
    private boolean defaultSet;

    /**
     * 添加一条 if 规则 —— 当 condition 为 true 时执行 action.
     * 规则按添加顺序依次评估，不支持插队.
     *
     * @param condition 判断当前上下文是否命中规则的条件，不允许为 {@code null}
     * @param action 命中规则后执行的动作，不允许为 {@code null}
     * @return 当前构建器，便于继续链式添加规则
     * @throws NullPointerException 当 {@code condition} 或 {@code action} 为 {@code null}
     * @throws IllegalStateException 当已经设置 {@link #otherwise(Function)} 时
     */
    public DecisionChainBuilder<T, R> when(Predicate<T> condition, Function<T, R> action) {
        if (defaultSet) {
            // 默认规则必然命中，其后的普通规则永远不可达，因此在构建阶段立即拒绝。
            throw new IllegalStateException("when cannot be added after otherwise");
        }
        Predicate<T> requiredCondition =
                Objects.requireNonNull(condition, "condition must not be null");
        Function<T, R> requiredAction =
                Objects.requireNonNull(action, "action must not be null");
        rules.add(new DecisionChain.DecisionRule<>(requiredCondition, requiredAction, false));
        return this;
    }

    /**
     * 添加兜底规则 —— 当所有 {@link #when} 都未命中时执行.
     * 只能调用一次，重复调用会抛异常.
     *
     * @param action 所有普通规则均未命中时执行的动作，不允许为 {@code null}
     * @return 当前构建器，便于调用 {@link #build()}
     * @throws NullPointerException 当 {@code action} 为 {@code null}
     * @throws IllegalStateException 当默认规则已经设置时
     */
    public DecisionChainBuilder<T, R> otherwise(Function<T, R> action) {
        if (defaultSet) {
            throw new IllegalStateException("otherwise already set");
        }
        Function<T, R> requiredAction =
                Objects.requireNonNull(action, "action must not be null");
        rules.add(new DecisionChain.DecisionRule<>(null, requiredAction, true));
        defaultSet = true;
        return this;
    }

    /**
     * 根据当前已注册规则构建决策链。
     *
     * @return 拥有独立规则快照的决策链
     * @throws IllegalStateException 当没有注册任何普通规则或默认规则时
     */
    public DecisionChain<T, R> build() {
        if (rules.isEmpty()) {
            throw new IllegalStateException("At least one rule (when or otherwise) required");
        }
        return new DecisionChain<>(rules);
    }
}
