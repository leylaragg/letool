package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;

import java.util.List;
import java.util.Objects;

/**
 * 一个页面序列中可独立排版的正文或目录单元。
 *
 * @author leyland
 */
final class PdfSequenceUnit {

    /** 排版单元进入后续管线时的固定类型。 */
    enum Kind {
        /** 普通正文，允许为空以保留空页面序列。 */
        BODY,
        /** 由目录声明独占的动态目录。 */
        TOC
    }

    /** 当前单元类型。 */
    private final Kind kind;

    /** 正文单元包含的块节点。 */
    private final List<BlockNode> blocks;

    /** 目录单元对应的声明。 */
    private final TableOfContentsNode tableOfContents;

    /** 创建一个正文单元，并冻结当前序列中的块节点。 */
    static PdfSequenceUnit body(List<BlockNode> blocks) {
        return new PdfSequenceUnit(Kind.BODY,
                List.copyOf(Objects.requireNonNull(blocks, "blocks 不能为空")), null);
    }

    /** 创建一个目录独占单元。 */
    static PdfSequenceUnit tableOfContents(TableOfContentsNode tableOfContents) {
        return new PdfSequenceUnit(Kind.TOC, List.of(),
                Objects.requireNonNull(tableOfContents, "tableOfContents 不能为空"));
    }

    /** 保存已经完成类型约束的单元状态。 */
    private PdfSequenceUnit(
            Kind kind,
            List<BlockNode> blocks,
            TableOfContentsNode tableOfContents) {
        this.kind = kind;
        this.blocks = blocks;
        this.tableOfContents = tableOfContents;
    }

    /** @return 当前排版单元类型 */
    Kind kind() {
        return kind;
    }

    /** @return 正文块节点；目录单元返回空列表 */
    List<BlockNode> blocks() {
        return blocks;
    }

    /** @return 目录声明；正文单元返回 {@code null} */
    TableOfContentsNode tableOfContents() {
        return tableOfContents;
    }
}
