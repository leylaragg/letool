package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;

import java.util.Objects;

/**
 * 单个已求值 AST 节点的不可变安全轨迹。
 */
public final class TraceNode {

    /** 轨迹节点结果分类。 */
    public enum ResultCategory {
        /** 节点产生了值。 */
        VALUE,
        /** 节点求值失败。 */
        FAILURE
    }

    /** 不含包名的 AST 节点类型。 */
    private final String nodeType;

    /** 节点的 UTF-16 起始偏移。 */
    private final int startPosition;

    /** 节点的 UTF-16 结束偏移。 */
    private final int endPosition;

    /** 节点成功产值或失败的固定分类。 */
    private final ResultCategory resultCategory;

    /** 成功值类型；失败节点使用未知占位类型。 */
    private final TypeDescriptor type;

    /** 已净化并限制长度的安全摘要。 */
    private final String summary;

    TraceNode(String nodeType, int startPosition, int endPosition,
            ResultCategory resultCategory, TypeDescriptor type, String summary) {
        if (nodeType == null || nodeType.isBlank() || startPosition < 0
                || endPosition <= startPosition || resultCategory == null
                || type == null || summary == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.nodeType = nodeType;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.resultCategory = resultCategory;
        this.type = type;
        this.summary = summary;
    }

    /** @return 稳定节点类型 */
    public String nodeType() { return nodeType; }

    /** @return 包含的源码起始位置 */
    public int startPosition() { return startPosition; }

    /** @return 不包含的源码结束位置 */
    public int endPosition() { return endPosition; }

    /** @return 值或失败分类 */
    public ResultCategory resultCategory() { return resultCategory; }

    /** @return 节点运行期类型 */
    public TypeDescriptor type() { return type; }

    /** @return 有界安全摘要 */
    public String summary() { return summary; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TraceNode that)) return false;
        return startPosition == that.startPosition && endPosition == that.endPosition
                && nodeType.equals(that.nodeType) && resultCategory == that.resultCategory
                && type.equals(that.type) && summary.equals(that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeType, startPosition, endPosition, resultCategory, type, summary);
    }
}
