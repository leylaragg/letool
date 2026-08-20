package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.BlockNode;

import java.util.List;
import java.util.Objects;

/**
 * 连续使用同一页面布局、重复区域和页码规则的文档内容。
 *
 * @author leyland
 */
public final class PageSequence {

    /** 页面布局。 */
    private final PageLayout pageLayout;

    /** 重复页眉。 */
    private final PageRegion header;

    /** 重复页脚。 */
    private final PageRegion footer;

    /** 逻辑页码规则。 */
    private final PageNumbering pageNumbering;

    /** 不可修改的正文块节点。 */
    private final List<BlockNode> body;

    /**
     * 创建页面序列快照。
     *
     * @param pageLayout 页面布局
     * @param header 重复页眉
     * @param footer 重复页脚
     * @param pageNumbering 逻辑页码规则
     * @param body 正文块节点，允许为空
     */
    public PageSequence(PageLayout pageLayout, PageRegion header, PageRegion footer,
            PageNumbering pageNumbering, List<BlockNode> body) {
        this.pageLayout = Objects.requireNonNull(pageLayout, "pageLayout 不能为空");
        this.header = Objects.requireNonNull(header, "header 不能为空");
        this.footer = Objects.requireNonNull(footer, "footer 不能为空");
        this.pageNumbering = Objects.requireNonNull(pageNumbering, "pageNumbering 不能为空");
        this.body = List.copyOf(body);
    }

    /**
     * 创建没有页眉页脚并延续逻辑页码的普通页面序列。
     *
     * @param pageLayout 页面布局
     * @param body 正文块节点
     * @return 普通页面序列
     */
    public static PageSequence body(PageLayout pageLayout, List<BlockNode> body) {
        return new PageSequence(pageLayout, PageRegion.empty(), PageRegion.empty(),
                PageNumbering.counted(), body);
    }

    /** @return 页面布局 */
    public PageLayout pageLayout() {
        return pageLayout;
    }

    /** @return 重复页眉 */
    public PageRegion header() {
        return header;
    }

    /** @return 重复页脚 */
    public PageRegion footer() {
        return footer;
    }

    /** @return 逻辑页码规则 */
    public PageNumbering pageNumbering() {
        return pageNumbering;
    }

    /** @return 不可修改的正文块节点 */
    public List<BlockNode> body() {
        return body;
    }
}
