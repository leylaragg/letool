package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

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

    /** 可选段落样式名。 */
    private final String styleName;

    /** 不可变行内内容。 */
    private final List<InlineNode> children;

    /**
     * 创建使用框架默认段落样式的标题。
     *
     * @param id 标题逻辑 ID
     * @param level 标题级别
     * @param children 非空行内内容
     */
    public HeadingNode(String id, int level, List<InlineNode> children) {
        this(id, level, "", children);
    }

    /**
     * 创建不可变标题。
     *
     * @param id 标题逻辑 ID
     * @param level 标题级别
     * @param styleName 段落样式名；空字符串表示使用框架默认样式
     * @param children 非空行内内容
     */
    public HeadingNode(String id, int level, String styleName, List<InlineNode> children) {
        this.id = NodeValidation.optionalId(id);
        this.styleName = styleName == null ? "" : styleName;
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

    /** @return 段落样式名 */
    public String styleName() {
        return styleName;
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
        return level == that.level && id.equals(that.id) && styleName.equals(that.styleName)
                && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, styleName, children);
    }

    @Override
    public String toString() {
        return "HeadingNode[id=" + id + ", level=" + level + ", styleName=" + styleName
                + ", children=" + children + "]";
    }
}
