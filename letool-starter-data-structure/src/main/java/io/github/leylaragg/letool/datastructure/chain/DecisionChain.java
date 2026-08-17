package io.github.leylaragg.letool.datastructure.chain;

import io.github.leylaragg.letool.datastructure.exception.DataStructureException;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 将条件判断和执行动作组织成有序规则的不可变决策链。
 *
 * <p>决策链按注册顺序评估规则，首个命中立即执行并返回。构建完成后规则结构不可修改，
 * 可安全并发读取；用户提供的条件和动作是否线程安全仍由业务实现保证。</p>
 *
 * <pre>{@code
 * DecisionChain<Order, String> chain = DecisionChain.<Order, String>builder()
 *     .when(Order::isVip, order -> "VIP通道")
 *     .when(order -> order.getAmount() > 10_000, order -> "大额通道")
 *     .otherwise(order -> "普通通道")
 *     .build();
 * }</pre>
 *
 * @param <T> 决策上下文类型
 * @param <R> 决策结果类型
 */
public final class DecisionChain<T, R> {

    /** 构建时复制得到的不可变有序规则快照。 */
    private final List<DecisionRule<T, R>> rules;

    /** 是否包含最终默认规则。 */
    private final boolean hasDefault;

    /**
     * 使用构建器中已经校验的规则创建决策链。
     *
     * @param rules 按匹配优先级排列的规则集合
     */
    DecisionChain(List<DecisionRule<T, R>> rules) {
        this.rules = List.copyOf(rules);
        this.hasDefault = this.rules.stream().anyMatch(DecisionRule::isDefault);
    }

    /**
     * 执行决策链，首个匹配规则的动作结果即为最终结果。
     *
     * <p>用户条件和动作抛出的异常保持原类型传播。没有规则命中时，异常不会调用或输出上下文对象，
     * 避免业务数据进入日志和响应。</p>
     *
     * @param context 决策上下文，可按业务规则允许为 {@code null}
     * @return 首个匹配规则的动作结果
     * @throws DataStructureException 当没有规则命中且未配置默认动作时抛出
     */
    public R execute(T context) {
        for (DecisionRule<T, R> rule : rules) {
            // 首个命中立即返回，后续规则不会执行。
            if (rule.matches(context)) {
                return rule.execute(context);
            }
        }
        throw DataStructureException.decisionNotMatched();
    }

    /**
     * 获取当前决策链规则数量，包含可选默认规则。
     *
     * @return 正整数规则数量
     */
    public int size() {
        return rules.size();
    }

    /**
     * 判断是否配置了最终默认规则。
     *
     * @return 配置了 {@code otherwise} 时返回 {@code true}
     */
    public boolean hasDefault() {
        return hasDefault;
    }

    /**
     * 创建尚未注册规则的构建器。
     *
     * @param <T> 决策上下文类型
     * @param <R> 决策结果类型
     * @return 新的决策链构建器
     */
    public static <T, R> DecisionChainBuilder<T, R> builder() {
        return new DecisionChainBuilder<>();
    }

    /**
     * 创建仅包含一条无条件动作的决策链。
     *
     * @param action 接收上下文并返回结果的无条件动作
     * @param <T> 决策上下文类型
     * @param <R> 决策结果类型
     * @return 只包含默认动作的决策链
     * @throws DataStructureException 当动作为 {@code null} 时抛出
     */
    public static <T, R> DecisionChain<T, R> of(Function<T, R> action) {
        return DecisionChain.<T, R>builder().otherwise(action).build();
    }

    /**
     * 决策链内部的一条不可变规则。
     *
     * @param <T> 决策上下文类型
     * @param <R> 决策结果类型
     */
    static final class DecisionRule<T, R> {

        /** 普通规则的匹配条件；默认规则没有条件。 */
        private final Predicate<T> condition;

        /** 规则命中后执行的动作。 */
        private final Function<T, R> action;

        /** 是否为始终命中的默认规则。 */
        private final boolean defaultRule;

        /**
         * 创建一条已完成参数校验的规则。
         *
         * @param condition 普通规则条件；默认规则允许为 {@code null}
         * @param action 规则动作
         * @param defaultRule 是否为默认规则
         */
        DecisionRule(Predicate<T> condition, Function<T, R> action, boolean defaultRule) {
            this.condition = condition;
            this.action = action;
            this.defaultRule = defaultRule;
        }

        /**
         * 判断规则是否匹配当前上下文。
         *
         * @param context 当前决策上下文
         * @return 默认规则或条件返回 {@code true} 时返回 {@code true}
         */
        boolean matches(T context) {
            return defaultRule || condition.test(context);
        }

        /**
         * 执行规则动作。
         *
         * @param context 当前决策上下文
         * @return 动作计算结果
         */
        R execute(T context) {
            return action.apply(context);
        }

        /**
         * 判断是否为默认规则。
         *
         * @return 默认规则返回 {@code true}
         */
        boolean isDefault() {
            return defaultRule;
        }
    }
}
