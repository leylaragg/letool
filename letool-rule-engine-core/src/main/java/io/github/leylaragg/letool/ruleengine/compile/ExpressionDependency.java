package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.FactPath;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;

import java.util.Objects;

/**
 * 一条带编译期预期类型和首次源码范围的事实依赖。
 */
public final class ExpressionDependency {

    /** 规范化后的事实路径。 */
    private final FactPath path;

    /** 路径在编译位置所需的类型，包含可空性。 */
    private final TypeDescriptor expectedType;

    /** 首次引用的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 首次引用的 UTF-16 结束偏移，不包含该位置。 */
    private final int endPosition;

    /**
     * 创建类型化依赖。
     *
     * @param path 规范事实路径
     * @param expectedType 包含可空性的预期类型
     * @param startPosition 源码起始位置
     * @param endPosition 源码结束位置
     */
    public ExpressionDependency(FactPath path, TypeDescriptor expectedType,
            int startPosition, int endPosition) {
        if (path == null || expectedType == null
                || startPosition < 0 || endPosition <= startPosition) {
            throw RuleEngineException.invalidArgument();
        }
        this.path = path;
        this.expectedType = expectedType;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /** @return 规范事实路径 */
    public FactPath path() { return path; }

    /** @return 编译期预期类型 */
    public TypeDescriptor expectedType() { return expectedType; }

    /** @return 包含的源码起始位置 */
    public int startPosition() { return startPosition; }

    /** @return 不包含的源码结束位置 */
    public int endPosition() { return endPosition; }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExpressionDependency that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && path.equals(that.path) && expectedType.equals(that.expectedType);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(path, expectedType, startPosition, endPosition);
    }
}
