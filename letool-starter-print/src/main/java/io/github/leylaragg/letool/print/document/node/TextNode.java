package io.github.leylaragg.letool.print.document.node;

import java.util.Objects;

/**
 * 保留原始文字和可选文本样式引用的行内节点。
 *
 * @author leyland
 */
public final class TextNode implements InlineNode {

    /** 原始文本内容。 */
    private final String text;

    /** 可选文本样式名。 */
    private final String styleName;

    /**
     * 创建使用段落或框架默认样式的文本节点。
     *
     * @param text 原始文本，允许空字符串
     */
    public TextNode(String text) {
        this(text, "");
    }

    /**
     * 创建文本节点。
     *
     * @param text 原始文本，允许空字符串
     * @param styleName 文本样式名；空字符串表示使用段落或框架默认样式
     */
    public TextNode(String text, String styleName) {
        this.text = Objects.requireNonNull(text, "text 不能为空");
        this.styleName = styleName == null ? "" : styleName;
    }

    /** @return 原始文本 */
    public String text() {
        return text;
    }

    /** @return 文本样式名 */
    public String styleName() {
        return styleName;
    }

    /** @return 空字符串，文本节点不参与逻辑定位 */
    @Override
    public String id() {
        return "";
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof TextNode that
                && text.equals(that.text) && styleName.equals(that.styleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, styleName);
    }

    @Override
    public String toString() {
        return "TextNode[text=" + text + ", styleName=" + styleName + "]";
    }
}
