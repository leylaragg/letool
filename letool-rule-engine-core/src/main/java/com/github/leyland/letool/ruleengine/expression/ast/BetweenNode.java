package com.github.leyland.letool.ruleengine.expression.ast;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.List;
import java.util.Objects;

/**
 * 不可变 BETWEEN 三操作数节点。
 */
public final class BetweenNode implements AstNode {

    /** 待判断值。 */
    private final AstNode value;

    /** 包含式下界。 */
    private final AstNode lowerBound;

    /** 包含式上界。 */
    private final AstNode upperBound;

    /** 按值、下界、上界顺序冻结的子节点视图。 */
    private final List<AstNode> children;

    /** 节点的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 节点的 UTF-16 结束偏移。 */
    private final int endPosition;

    /**
     * 创建 BETWEEN 节点。
     *
     * @param value 待判断值
     * @param lowerBound 下界
     * @param upperBound 上界
     * @param startPosition 源码起始位置
     * @param endPosition 源码结束位置
     */
    public BetweenNode(
            AstNode value, AstNode lowerBound, AstNode upperBound,
            int startPosition, int endPosition) {
        if (value == null || lowerBound == null || upperBound == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.children = AstNodes.copyChildren(
                List.of(value, lowerBound, upperBound), true,
                startPosition, endPosition);
        this.value = value;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /** @return 待判断值 */
    public AstNode value() {
        return value;
    }

    /** @return 下界 */
    public AstNode lowerBound() {
        return lowerBound;
    }

    /** @return 上界 */
    public AstNode upperBound() {
        return upperBound;
    }

    /** {@inheritDoc} */
    @Override
    public int startPosition() {
        return startPosition;
    }

    /** {@inheritDoc} */
    @Override
    public int endPosition() {
        return endPosition;
    }

    /** {@inheritDoc} */
    @Override
    public List<AstNode> children() {
        return children;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BetweenNode that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && value.equals(that.value) && lowerBound.equals(that.lowerBound)
                && upperBound.equals(that.upperBound);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(value, lowerBound, upperBound, startPosition, endPosition);
    }
}
