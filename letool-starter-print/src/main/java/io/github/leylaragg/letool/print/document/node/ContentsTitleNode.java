package io.github.leylaragg.letool.print.document.node;

import java.util.Objects;

/**
 * 测量目录的可见标题，不产生空占位目标。
 *
 * @param id 标题逻辑 ID；null 或空字符串表示没有独立目标
 * @param title 可见标题文字
 * @param styleName 标题段落样式名；null 或空字符串使用默认样式
 * @param layout 目录测量布局，长度单位为点
 * @author leyland
 * @date 2026/10/14
 */
public record ContentsTitleNode(String id, String title, String styleName, TableOfContentsLayout layout)
    implements BlockNode {
    /**
     * 创建并校验不可变节点。
     *
     * @param id 标题逻辑 ID；null 或空字符串表示没有独立目标
     * @param title 可见标题文字
     * @param styleName 标题段落样式名；null 或空字符串使用默认样式
     * @param layout 目录测量布局，长度单位为点
     * @throws NullPointerException 标题或布局为 null
     * @throws io.github.leylaragg.letool.print.exception.PrintValidationException 标题 ID 不合法
     */
    public ContentsTitleNode {
        id = NodeValidation.optionalId(id);
        Objects.requireNonNull(title, "title 不能为空");
        Objects.requireNonNull(layout, "layout 不能为空");
        styleName = styleName == null ? "" : styleName;
    }
}
