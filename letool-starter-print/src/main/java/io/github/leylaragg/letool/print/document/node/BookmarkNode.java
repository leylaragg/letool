package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

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

    /** 可选正文目标；空值保持当前位置语义。 */
    private final String targetId;

    /** 可选父书签 ID。 */
    private final String parentId;

    /** 初始是否折叠子项。 */
    private final boolean collapsed;

    /**
     * 创建书签节点。
     *
     * @param id 必填逻辑 ID
     * @param label 非空白书签标签
     */
    public BookmarkNode(String id, String label) {
        this(id, label, "", "", false);
    }

    /**
     * 创建树形书签；未指定目标时在当前位置建立命名目标。
     *
     * @param id 必填逻辑 ID
     * @param label 非空白书签标签
     * @param targetId 可选内容目标 ID；null 或空字符串表示当前位置
     * @param parentId 可选父书签 ID；null 或空字符串表示根书签
     * @param collapsed 初始是否折叠子项
     * @throws PrintValidationException ID 不合法或书签标签为空白
     */
    public BookmarkNode(String id, String label, String targetId, String parentId, boolean collapsed) {
        this.id = NodeValidation.requiredId(id);
        if (label == null || label.isBlank()) {
            throw PrintValidationException.invalidDocument("书签标签不能为空");
        }
        this.label = label;
        this.targetId = NodeValidation.optionalId(targetId);
        this.parentId = NodeValidation.optionalId(parentId);
        this.collapsed = collapsed;
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

    /** @return 可选正文目标 ID；空值表示当前位置 */
    public String targetId() {
        return targetId;
    }

    /** @return 实际导航目标 ID */
    public String effectiveTargetId() {
        return targetId.isEmpty() ? id : targetId;
    }

    /** @return 可选父书签 ID */
    public String parentId() {
        return parentId;
    }

    /** @return 初始是否折叠子项 */
    public boolean collapsed() {
        return collapsed;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BookmarkNode that)) {
            return false;
        }
        return id.equals(that.id) && label.equals(that.label)
            && targetId.equals(that.targetId) && parentId.equals(that.parentId)
            && collapsed == that.collapsed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, targetId, parentId, collapsed);
    }

    @Override
    public String toString() {
        return "BookmarkNode[id=" + id + ", label=" + label + "]";
    }
}
