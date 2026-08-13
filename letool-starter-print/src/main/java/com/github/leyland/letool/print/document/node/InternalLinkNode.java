package com.github.leyland.letool.print.document.node;

import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.List;
import java.util.Objects;

/**
 * 指向文档内逻辑目标的链接。
 *
 * @author leyland
 */
public final class InternalLinkNode implements InlineNode {

    /** 目标节点 ID。 */
    private final String targetId;

    /** 不可变行内标签。 */
    private final List<InlineNode> label;

    /**
     * 创建内部链接。
     *
     * @param targetId 目标节点 ID
     * @param label 非空行内标签
     */
    public InternalLinkNode(String targetId, List<InlineNode> label) {
        this.targetId = NodeValidation.requiredId(targetId);
        this.label = List.copyOf(label);
        if (this.label.isEmpty()) {
            throw PrintValidationException.invalidDocument("内部链接标签不能为空");
        }
        if (this.label.stream().anyMatch(InternalLinkNode.class::isInstance)) {
            throw PrintValidationException.invalidDocument("内部链接标签不能嵌套内部链接");
        }
    }

    /** @return 目标节点 ID */
    public String targetId() {
        return targetId;
    }

    /** @return 不可变行内标签 */
    public List<InlineNode> label() {
        return label;
    }

    /** @return 空字符串，链接自身不是目标节点 */
    @Override
    public String id() {
        return "";
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof InternalLinkNode that)) {
            return false;
        }
        return targetId.equals(that.targetId) && label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetId, label);
    }

    @Override
    public String toString() {
        return "InternalLinkNode[targetId=" + targetId + ", label=" + label + "]";
    }
}
