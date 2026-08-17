package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.List;
import java.util.Objects;

/**
 * 具有可选逻辑 ID 的文档章节。
 *
 * @author leyland
 */
public final class SectionNode implements BlockNode {

    /** 章节逻辑 ID。 */
    private final String id;

    /** 不可变块级子节点。 */
    private final List<BlockNode> children;

    /**
     * 创建不可变章节。
     *
     * @param id 章节逻辑 ID
     * @param children 非空块级子节点
     */
    public SectionNode(String id, List<BlockNode> children) {
        this.id = NodeValidation.optionalId(id);
        this.children = List.copyOf(children);
        if (this.children.isEmpty()) {
            throw PrintValidationException.invalidDocument("章节至少包含一个子节点");
        }
    }

    /** @return 章节逻辑 ID */
    @Override
    public String id() {
        return id;
    }

    /** @return 不可变块级子节点 */
    public List<BlockNode> children() {
        return children;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SectionNode that)) {
            return false;
        }
        return id.equals(that.id) && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, children);
    }

    @Override
    public String toString() {
        return "SectionNode[id=" + id + ", children=" + children + "]";
    }
}
