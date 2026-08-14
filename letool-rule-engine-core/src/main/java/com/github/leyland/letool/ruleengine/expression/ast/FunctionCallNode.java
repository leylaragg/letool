package com.github.leyland.letool.ruleengine.expression.ast;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 保存函数编码和参数语法树的不可变调用节点。
 */
public final class FunctionCallNode implements AstNode {

    /** 规范函数编码允许的字符形式。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    /** 规范大写函数编码。 */
    private final String code;

    /** 按源码顺序冻结的调用参数。 */
    private final List<AstNode> arguments;

    /** 函数名的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 右括号后的 UTF-16 结束偏移。 */
    private final int endPosition;

    /**
     * 创建函数调用节点。
     *
     * @param code 规范大写函数编码
     * @param arguments 按源码顺序排列的参数，可为空
     * @param startPosition 源码起始位置
     * @param endPosition 源码结束位置
     */
    public FunctionCallNode(
            String code, List<? extends AstNode> arguments,
            int startPosition, int endPosition) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw RuleEngineException.invalidArgument();
        }
        this.arguments = AstNodes.copyChildren(
                arguments, false, startPosition, endPosition);
        this.code = code;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    /** @return 规范大写函数编码 */
    public String code() {
        return code;
    }

    /** @return 不可变参数列表 */
    public List<AstNode> arguments() {
        return arguments;
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
        return arguments;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FunctionCallNode that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && code.equals(that.code) && arguments.equals(that.arguments);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(code, arguments, startPosition, endPosition);
    }
}
