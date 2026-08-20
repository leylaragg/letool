package io.github.leylaragg.letool.print.document.node;

import java.util.List;
import java.util.Objects;

/**
 * 文档段落；空内容表示模板明确声明的留白段落。
 *
 * @author leyland
 */
public final class ParagraphNode implements BlockNode {

    /** 段落逻辑 ID。 */
    private final String id;

    /** 不可变行内内容。 */
    private final List<InlineNode> children;

    /** 可选段落样式名。 */
    private final String styleName;

    /**
     * 创建使用框架默认段落样式的节点。
     *
     * @param id 段落逻辑 ID
     * @param children 行内内容
     */
    public ParagraphNode(String id, List<InlineNode> children) {
        this(id, "", children);
    }

    /**
     * 创建不可变段落。
     *
     * @param id 段落逻辑 ID
     * @param styleName 段落样式名；空字符串表示使用框架默认样式
     * @param children 行内内容
     */
    public ParagraphNode(String id, String styleName, List<InlineNode> children) {
        this.id = NodeValidation.optionalId(id);
        this.styleName = styleName == null ? "" : styleName;
        this.children = List.copyOf(children);
    }

    /** @return 段落逻辑 ID */
    @Override
    public String id() {
        return id;
    }

    /** @return 不可变行内内容 */
    public List<InlineNode> children() {
        return children;
    }

    /** @return 段落样式名 */
    public String styleName() {
        return styleName;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ParagraphNode that)) {
            return false;
        }
        return id.equals(that.id) && styleName.equals(that.styleName)
                && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, styleName, children);
    }

    @Override
    public String toString() {
        return "ParagraphNode[id=" + id + ", styleName=" + styleName
                + ", children=" + children + "]";
    }
}
