package com.github.leyland.letool.print.document.node;

import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 在当前位置建立命名目标和书签标签的行内节点。
 *
 * @author leyland
 */
public final class BookmarkNode implements InlineNode {

    /** 必填逻辑 ID。 */
    private final String id;

    /** 非空白书签标签。 */
    private final String label;

    /**
     * 创建书签节点。
     *
     * @param id 必填逻辑 ID
     * @param label 非空白书签标签
     */
    public BookmarkNode(String id, String label) {
        this.id = NodeValidation.requiredId(id);
        if (label == null || label.isBlank()) {
            throw PrintValidationException.invalidDocument("书签标签不能为空");
        }
        this.label = label;
    }

    /** @return 必填逻辑 ID */
    @Override
    public String id() {
        return id;
    }

    /** @return 书签标签 */
    public String label() {
        return label;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BookmarkNode that)) {
            return false;
        }
        return id.equals(that.id) && label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label);
    }

    @Override
    public String toString() {
        return "BookmarkNode[id=" + id + ", label=" + label + "]";
    }
}
