package io.github.leylaragg.letool.ruleengine.expression.ast;

import java.util.List;
import java.util.Objects;

/**
 * IN 右侧非空表达式列表的不可变节点。
 */
public final class ListLiteralNode implements AstNode {

    /** 按源码顺序冻结的非空元素。 */
    private final List<AstNode> elements;

    /** 左括号的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 右括号后的 UTF-16 结束偏移。 */
    private final int endPosition;

    /**
     * 创建非空列表节点。
     *
     * @param elements 按源码顺序排列的非空元素列表
     * @param startPosition 左括号起始位置
     * @param endPosition 右括号结束位置
     */
    public ListLiteralNode(
            List<? extends AstNode> elements, int startPosition, int endPosition) {
        this.elements = AstNodes.copyChildren(
                elements, true, startPosition, endPosition);
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /** @return 不可变元素列表 */
    public List<AstNode> elements() {
        return elements;
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
        return elements;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ListLiteralNode that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && elements.equals(that.elements);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(elements, startPosition, endPosition);
    }
}
