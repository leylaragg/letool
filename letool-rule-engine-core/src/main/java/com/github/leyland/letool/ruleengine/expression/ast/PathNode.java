package com.github.leyland.letool.ruleengine.expression.ast;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.List;
import java.util.Objects;

/**
 * 保存 Lexer 规范路径文本的不可变节点。
 */
public final class PathNode implements AstNode {

    /** Lexer 生成的规范事实路径文本。 */
    private final String normalizedPath;

    /** 节点的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 节点的 UTF-16 结束偏移。 */
    private final int endPosition;

    /**
     * 创建路径节点；该构造器不执行事实路径业务解析。
     *
     * @param normalizedPath 规范路径文本；语义合法性由后续分析阶段判断
     * @param startPosition 源码起始位置
     * @param endPosition 源码结束位置
     */
    public PathNode(String normalizedPath, int startPosition, int endPosition) {
        AstNodes.requireRange(startPosition, endPosition);
        if (normalizedPath == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.normalizedPath = normalizedPath;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /** @return Lexer 规范路径文本 */
    public String normalizedPath() {
        return normalizedPath;
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
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PathNode that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && normalizedPath.equals(that.normalizedPath);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(normalizedPath, startPosition, endPosition);
    }
}
