package com.github.leyland.letool.datastructure.chain;

import com.github.leyland.letool.datastructure.exception.DataStructureException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@link DecisionChain} 的有序规则构建器。
 *
 * <p>默认规则必须位于最后且只能配置一次。构建操作生成独立快照，构建器在未添加默认规则前
 * 可以继续追加普通规则并创建新的决策链。</p>
 *
 * @param <T> 决策上下文类型
 * @param <R> 决策结果类型
 */
public final class DecisionChainBuilder<T, R> {

    /** 按注册顺序保存普通规则和可选默认规则。 */
    private final List<DecisionChain.DecisionRule<T, R>> rules = new ArrayList<>();

    /** 默认规则是否已经设置。 */
    private boolean defaultSet;

    /**
     * 添加一条条件规则。
     *
     * @param condition 非空匹配条件
     * @param action 条件命中后的非空动作
     * @return 当前构建器
     * @throws DataStructureException 当参数为空或默认规则已经设置时抛出
     */
    public DecisionChainBuilder<T, R> when(Predicate<T> condition, Function<T, R> action) {
        if (defaultSet) {
            // 默认规则必然命中，其后的普通规则不可达，因此在构建阶段拒绝。
            throw DataStructureException.invalidArgument("ruleOrder");
        }
        if (condition == null) {
            throw DataStructureException.invalidArgument("condition");
        }
        if (action == null) {
            throw DataStructureException.invalidArgument("action");
        }
        rules.add(new DecisionChain.DecisionRule<>(condition, action, false));
        return this;
    }

    /**
     * 添加最终兜底规则。
     *
     * @param action 所有普通规则均未命中时执行的非空动作
     * @return 当前构建器
     * @throws DataStructureException 当动作为空或默认规则已经设置时抛出
     */
    public DecisionChainBuilder<T, R> otherwise(Function<T, R> action) {
        if (defaultSet) {
            throw DataStructureException.invalidArgument("defaultRule");
        }
        if (action == null) {
            throw DataStructureException.invalidArgument("action");
        }
        rules.add(new DecisionChain.DecisionRule<>(null, action, true));
        defaultSet = true;
        return this;
    }

    /**
     * 根据当前规则快照构建不可变决策链。
     *
     * @return 拥有独立规则快照的决策链
     * @throws DataStructureException 当没有注册任何规则时抛出
     */
    public DecisionChain<T, R> build() {
        if (rules.isEmpty()) {
            throw DataStructureException.invalidArgument("rules");
        }
        return new DecisionChain<>(rules);
    }
}
