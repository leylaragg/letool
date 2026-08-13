package com.github.leyland.letool.print.document.node;

import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.List;
import java.util.Objects;

/**
 * 一至六级文档标题。
 *
 * @author leyland
 */
public final class HeadingNode implements BlockNode {

    /** 标题逻辑 ID。 */
    private final String id;

    /** 标题级别。 */
    private final int level;

    /** 不可变行内内容。 */
    private final List<InlineNode> children;

    /**
     * 创建不可变标题。
     *
     * @param id 标题逻辑 ID
     * @param level 标题级别
     * @param children 非空行内内容
     */
    public HeadingNode(String id, int level, List<InlineNode> children) {
        this.id = NodeValidation.optionalId(id);
        this.children = List.copyOf(children);
        if (level < 1 || level > 6) {
            throw PrintValidationException.invalidDocument("标题级别必须在 1 到 6 之间");
        }
        if (this.children.isEmpty()) {
            throw PrintValidationException.invalidDocument("标题内容不能为空");
        }
        this.level = level;
    }

    /** @return 标题逻辑 ID */
    @Override
    public String id() {
        return id;
    }

    /** @return 标题级别 */
    public int level() {
        return level;
    }

    /** @return 不可变行内内容 */
    public List<InlineNode> children() {
        return children;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof HeadingNode that)) {
            return false;
        }
        return level == that.level && id.equals(that.id) && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, children);
    }

    @Override
    public String toString() {
        return "HeadingNode[id=" + id + ", level=" + level + ", children=" + children + "]";
    }
}
