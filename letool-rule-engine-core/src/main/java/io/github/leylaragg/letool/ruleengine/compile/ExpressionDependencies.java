package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按首次源码出现顺序去重的不可变事实依赖集合。
 */
public final class ExpressionDependencies {

    /** 单个编译产物允许记录的依赖数量上限。 */
    private static final int MAX_DEPENDENCIES = 2_048;

    /** 按首次源码位置排列的依赖快照。 */
    private final List<ExpressionDependency> values;

    /** 接收已经去重并冻结的依赖列表。 */
    private ExpressionDependencies(List<ExpressionDependency> values) {
        this.values = values;
    }

    /**
     * 创建依赖集合；同一路径保留首次出现的范围。
     *
     * @param dependencies 源码顺序依赖
     * @return 不可变有序集合
     */
    public static ExpressionDependencies of(List<ExpressionDependency> dependencies) {
        if (dependencies == null) throw RuleEngineException.invalidArgument();
        try {
            Map<String, ExpressionDependency> unique = new LinkedHashMap<>();
            int visited = 0;
            for (ExpressionDependency dependency : dependencies) {
                if (++visited > MAX_DEPENDENCIES || dependency == null) {
                    throw RuleEngineException.invalidArgument();
                }
                ExpressionDependency previous = unique.putIfAbsent(
                        dependency.path().toString(), dependency);
                if (previous != null
                        && !previous.expectedType().equals(dependency.expectedType())) {
                    throw RuleEngineException.invalidArgument();
                }
            }
            return new ExpressionDependencies(Collections.unmodifiableList(
                    new ArrayList<>(unique.values())));
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /** @return 按首次出现顺序排列的不可变依赖 */
    public List<ExpressionDependency> values() { return values; }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ExpressionDependencies that
                && values.equals(that.values);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() { return Objects.hash(values); }
}
