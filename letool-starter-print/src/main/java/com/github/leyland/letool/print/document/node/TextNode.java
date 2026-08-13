package com.github.leyland.letool.print.document.node;

import java.util.Objects;

/**
 * 保留原始文本内容的行内节点。
 *
 * @param text 文本内容，允许空字符串但不允许 {@code null}
 * @author leyland
 */
public record TextNode(String text) implements InlineNode {

    /**
     * 创建文本节点。
     *
     * @param text 文本内容
     */
    public TextNode {
        Objects.requireNonNull(text, "text 不能为空");
    }

    /** @return 空字符串，文本节点不参与逻辑定位 */
    @Override
    public String id() {
        return "";
    }
}
