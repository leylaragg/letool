package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageNumbering;
import io.github.leylaragg.letool.print.document.PageRegion;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一个页面序列的版式快照和有序排版单元。
 *
 * @author leyland
 */
final class PdfSequencePlan {

    /** 单个序列也不能独占超过文档级单元上限。 */
    private static final int MAX_RENDER_UNITS = 1_000;

    /** 当前序列在原文档中的零基位置。 */
    private final int sourceIndex;

    /** 当前序列独立使用的页面布局。 */
    private final PageLayout pageLayout;

    /** 每页重复的页眉。 */
    private final PageRegion header;

    /** 每页重复的页脚。 */
    private final PageRegion footer;

    /** 当前序列的逻辑页码规则。 */
    private final PageNumbering pageNumbering;

    /** 只从当前序列正文切分得到的排版单元。 */
    private final List<PdfSequenceUnit> units;

    /** 当前序列的目录单元位置。 */
    private final int tableOfContentsIndex;

    /** 当前序列的正文单元数量。 */
    private final int bodyUnitCount;

    /**
     * 从一个页面序列建立独立排版计划。
     *
     * @param sourceIndex 序列在原文档中的位置
     * @param sequence 待切分的页面序列
     * @return 不会读取相邻序列的计划快照
     */
    static PdfSequencePlan create(int sourceIndex, PageSequence sequence) {
        if (sourceIndex < 0) {
            throw new IllegalArgumentException("sourceIndex 不能小于零");
        }
        Objects.requireNonNull(sequence, "sequence 不能为空");
        List<PdfSequenceUnit> units = new ArrayList<>();
        List<BlockNode> body = new ArrayList<>();
        int tocIndex = -1;
        int bodyCount = 0;

        for (int index = 0; index < sequence.body().size(); index++) {
            BlockNode block = sequence.body().get(index);
            if (block instanceof TableOfContentsNode tableOfContents) {
                bodyCount += flushBody(units, body);
                addUnit(units, PdfSequenceUnit.tableOfContents(tableOfContents));
                tocIndex = units.size() - 1;
            } else if (block == PageBreakNode.INSTANCE && !body.isEmpty()
                    && hasBodyAfter(sequence.body(), index + 1)) {
                bodyCount += flushBody(units, body);
            } else {
                body.add(block);
            }
        }
        bodyCount += flushBody(units, body);
        if (units.isEmpty()) {
            addUnit(units, PdfSequenceUnit.body(List.of()));
            bodyCount = 1;
        }
        return new PdfSequencePlan(sourceIndex, sequence,
                List.copyOf(units), tocIndex, bodyCount);
    }

    /** 保存页面语义和完成切分的单元列表。 */
    private PdfSequencePlan(
            int sourceIndex,
            PageSequence sequence,
            List<PdfSequenceUnit> units,
            int tableOfContentsIndex,
            int bodyUnitCount) {
        this.sourceIndex = sourceIndex;
        this.pageLayout = sequence.pageLayout();
        this.header = sequence.header();
        this.footer = sequence.footer();
        this.pageNumbering = sequence.pageNumbering();
        this.units = units;
        this.tableOfContentsIndex = tableOfContentsIndex;
        this.bodyUnitCount = bodyUnitCount;
    }

    /** 只查看当前序列中目录之前的节点，不能借用相邻序列正文。 */
    private static boolean hasBodyAfter(List<BlockNode> blocks, int start) {
        for (int index = start; index < blocks.size(); index++) {
            BlockNode candidate = blocks.get(index);
            if (candidate instanceof TableOfContentsNode) {
                return false;
            }
            if (candidate != PageBreakNode.INSTANCE) {
                return true;
            }
        }
        return false;
    }

    /** 把已经积累的正文冻结为一个排版单元。 */
    private static int flushBody(List<PdfSequenceUnit> units, List<BlockNode> body) {
        if (body.isEmpty()) {
            return 0;
        }
        addUnit(units, PdfSequenceUnit.body(body));
        body.clear();
        return 1;
    }

    /** 在修改列表前检查单元上限。 */
    private static void addUnit(List<PdfSequenceUnit> units, PdfSequenceUnit unit) {
        if (units.size() >= MAX_RENDER_UNITS) {
            throw PrintValidationException.invalidDocument("PDF 排版单元不能超过 1,000 个");
        }
        units.add(unit);
    }

    /** @return 当前序列在原文档中的零基位置 */
    int sourceIndex() {
        return sourceIndex;
    }

    /** @return 当前序列的页面布局 */
    PageLayout pageLayout() {
        return pageLayout;
    }

    /** @return 当前序列的重复页眉 */
    PageRegion header() {
        return header;
    }

    /** @return 当前序列的重复页脚 */
    PageRegion footer() {
        return footer;
    }

    /** @return 当前序列的逻辑页码规则 */
    PageNumbering pageNumbering() {
        return pageNumbering;
    }

    /** @return 保持正文顺序的排版单元 */
    List<PdfSequenceUnit> units() {
        return units;
    }

    /** @return 目录单元位置；没有目录时为 {@code -1} */
    int tableOfContentsIndex() {
        return tableOfContentsIndex;
    }

    /** @return 当前序列的正文单元数量 */
    int bodyUnitCount() {
        return bodyUnitCount;
    }
}
