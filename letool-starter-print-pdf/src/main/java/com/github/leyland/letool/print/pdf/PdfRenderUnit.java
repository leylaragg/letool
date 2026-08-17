package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.document.node.BlockNode;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;

import java.util.List;
import java.util.Objects;

/**
 * 一次独立排版的正文或目录单元。
 *
 * @author leyland
 */
final class PdfRenderUnit {

    /** 单元进入后续渲染管线时的固定类型。 */
    enum Kind {
        BODY,
        TOC
    }

    private final Kind kind;
    private final List<BlockNode> blocks;
    private final TableOfContentsNode tableOfContents;

    /** 创建一个非空正文单元。 */
    static PdfRenderUnit body(List<BlockNode> blocks) {
        List<BlockNode> snapshot = List.copyOf(blocks);
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("正文排版单元不能为空");
        }
        return new PdfRenderUnit(Kind.BODY, snapshot, null);
    }

    /** 创建由目录声明独占的排版单元。 */
    static PdfRenderUnit tableOfContents(TableOfContentsNode tableOfContents) {
        return new PdfRenderUnit(
                Kind.TOC, List.of(), Objects.requireNonNull(tableOfContents, "目录节点不能为空"));
    }

    private PdfRenderUnit(
            Kind kind,
            List<BlockNode> blocks,
            TableOfContentsNode tableOfContents) {
        this.kind = kind;
        this.blocks = blocks;
        this.tableOfContents = tableOfContents;
    }

    /** @return 单元类型 */
    Kind kind() {
        return kind;
    }

    /** @return 正文节点；目录单元返回空列表 */
    List<BlockNode> blocks() {
        return blocks;
    }

    /** @return 目录声明；正文单元返回 {@code null} */
    TableOfContentsNode tableOfContents() {
        return tableOfContents;
    }
}
