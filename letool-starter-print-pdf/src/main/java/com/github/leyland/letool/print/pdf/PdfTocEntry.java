package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.document.node.HeadingNode;

/**
 * 目录排版使用的标题和稳定目标。
 *
 * @author leyland
 */
final class PdfTocEntry {
    private final HeadingNode heading;
    private final String title;
    private final String targetId;

    /** 保存目录行需要的标题节点、文字和布局目标。 */
    PdfTocEntry(HeadingNode heading, String title, String targetId) {
        this.heading = heading;
        this.title = title;
        this.targetId = targetId;
    }

    /** @return 用于查询最终页码的标题节点 */
    HeadingNode heading() {
        return heading;
    }

    /** @return 目录中展示的标题文字 */
    String title() {
        return title;
    }

    /** @return 标题对应的稳定布局目标 */
    String targetId() {
        return targetId;
    }
}
