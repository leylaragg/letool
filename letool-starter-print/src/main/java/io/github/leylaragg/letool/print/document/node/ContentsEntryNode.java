package io.github.leylaragg.letool.print.document.node;

import java.util.Objects;

/**
 * 渲染器根据最终页码展开的通用目录条目。
 *
 * @param link 指向正文目标的目录标签链接
 * @param pageNumber 最终排版计算的显示页码
 * @param level 目录层级，范围为 1 到 6
 * @param keepWithNext 是否优先与下一个条目保持同页
 * @param titleId 本条目所属的目录标题 ID
 * @param layout 目录测量布局，长度单位为点
 * @author leyland
 * @date 2026/10/14
 */
public record ContentsEntryNode(InternalLinkNode link, String pageNumber, int level, boolean keepWithNext,
    String titleId, TableOfContentsLayout layout) implements BlockNode {
    /**
     * 创建并校验不可变节点。
     *
     * @param link 指向正文目标的目录标签链接
     * @param pageNumber 最终排版计算的显示页码
     * @param level 目录层级，范围为 1 到 6
     * @param keepWithNext 是否优先与下一个条目保持同页
     * @param titleId 本条目所属的目录标题 ID
     * @param layout 目录测量布局，长度单位为点
     * @throws NullPointerException 链接、显示页码或布局为 null
     * @throws IllegalArgumentException 层级超出范围
     * @throws io.github.leylaragg.letool.print.exception.PrintValidationException 标题 ID 不合法
     */
    public ContentsEntryNode {
        Objects.requireNonNull(link, "link 不能为空");
        Objects.requireNonNull(pageNumber, "pageNumber 不能为空");
        Objects.requireNonNull(layout, "layout 不能为空");
        titleId = NodeValidation.optionalId(titleId);
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("目录层级不合法");
        }
    }
    /** @return 空字符串，本节点不建立独立导航目标 */
    @Override
    public String id() {
        return "";
    }
}
