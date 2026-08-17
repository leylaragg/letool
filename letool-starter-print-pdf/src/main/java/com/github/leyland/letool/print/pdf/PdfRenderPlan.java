package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.node.BlockNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;
import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 从文档根块建立的不可变 PDF 排版计划。
 *
 * @author leyland
 */
final class PdfRenderPlan {

    /** 单次文档允许建立的排版单元数。 */
    static final int MAX_RENDER_UNITS = 1_000;

    private final List<PdfRenderUnit> units;
    private final int tableOfContentsIndex;
    private final int bodyUnitCount;

    /**
     * 按根目录声明和有效根分页切分排版单元。
     *
     * @param document 已完成全局校验的文档
     * @return 保持原始文档顺序的排版计划
     */
    static PdfRenderPlan create(DocumentModel document) {
        Objects.requireNonNull(document, "document 不能为空");
        List<PdfRenderUnit> units = new ArrayList<>();
        List<BlockNode> body = new ArrayList<>();
        List<BlockNode> blocks = document.blocks();
        int tocIndex = -1;
        int bodyCount = 0;

        for (int index = 0; index < blocks.size(); index++) {
            BlockNode block = blocks.get(index);
            if (block instanceof TableOfContentsNode tableOfContents) {
                bodyCount += flushBody(units, body);
                addUnit(units, PdfRenderUnit.tableOfContents(tableOfContents));
                tocIndex = units.size() - 1;
            } else if (block == PageBreakNode.INSTANCE
                    && !body.isEmpty()
                    && hasBodyAfter(blocks, index + 1)) {
                // 只有分页两侧都有正文时才把它消费为单元边界。
                bodyCount += flushBody(units, body);
            } else {
                body.add(block);
            }
        }
        bodyCount += flushBody(units, body);
        return new PdfRenderPlan(List.copyOf(units), tocIndex, bodyCount);
    }

    private PdfRenderPlan(List<PdfRenderUnit> units, int tableOfContentsIndex, int bodyUnitCount) {
        this.units = units;
        this.tableOfContentsIndex = tableOfContentsIndex;
        this.bodyUnitCount = bodyUnitCount;
    }

    /** 遇到目录前不跨越目录寻找分页右侧正文。 */
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

    /** 把已积累正文冻结为单元，空积累不产生占位。 */
    private static int flushBody(List<PdfRenderUnit> units, List<BlockNode> body) {
        if (body.isEmpty()) {
            return 0;
        }
        addUnit(units, PdfRenderUnit.body(body));
        body.clear();
        return 1;
    }

    /** 修改列表前执行上限检查，避免越界单元短暂进入计划。 */
    private static void addUnit(List<PdfRenderUnit> units, PdfRenderUnit unit) {
        if (units.size() >= MAX_RENDER_UNITS) {
            throw PrintValidationException.invalidDocument("PDF 排版单元不能超过 1,000 个");
        }
        units.add(unit);
    }

    /** @return 不可修改的有序排版单元 */
    List<PdfRenderUnit> units() {
        return units;
    }

    /** @return 目录单元索引；没有目录时返回 {@code -1} */
    int tableOfContentsIndex() {
        return tableOfContentsIndex;
    }

    /** @return 正文单元数量 */
    int bodyUnitCount() {
        return bodyUnitCount;
    }
}
