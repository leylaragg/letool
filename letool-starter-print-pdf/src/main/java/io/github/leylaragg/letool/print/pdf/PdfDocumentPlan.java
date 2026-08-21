package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 从完整文档建立的不可变 PDF 页面序列计划。
 *
 * @author leyland
 */
final class PdfDocumentPlan {

    /** 单份 PDF 允许建立的排版单元总量。 */
    static final int MAX_RENDER_UNITS = 1_000;

    /** 建立计划时使用的不可变文档模型。 */
    private final DocumentModel document;

    /** 完整文档元数据。 */
    private final DocumentMetadata metadata;

    /** 完整文档命名样式。 */
    private final StyleSheet styleSheet;

    /** 按原文档顺序保存的页面序列计划。 */
    private final List<PdfSequencePlan> sequences;

    /** 跨页面序列统一分配的布局 ID。 */
    private final PdfRenderIds renderIds;

    /** 全部页面序列的排版单元总量。 */
    private final int unitCount;

    /**
     * 为整份文档建立页面序列计划。
     *
     * @param document 已完成公共模型校验的文档
     * @return 不打开外部资源的 PDF 计划快照
     */
    static PdfDocumentPlan create(DocumentModel document) {
        Objects.requireNonNull(document, "document 不能为空");
        List<PdfSequencePlan> sequences = new ArrayList<>(document.pageSequences().size());
        int unitCount = 0;
        for (int index = 0; index < document.pageSequences().size(); index++) {
            PdfSequencePlan sequence = PdfSequencePlan.create(
                    index, document.pageSequences().get(index));
            unitCount = Math.addExact(unitCount, sequence.units().size());
            if (unitCount > MAX_RENDER_UNITS) {
                throw PrintValidationException.invalidDocument(
                        "PDF 排版单元不能超过 1,000 个");
            }
            sequences.add(sequence);
        }
        return new PdfDocumentPlan(document, List.copyOf(sequences), unitCount);
    }

    /** 保存完整文档范围内已经冻结的计划信息。 */
    private PdfDocumentPlan(
            DocumentModel document,
            List<PdfSequencePlan> sequences,
            int unitCount) {
        this.metadata = document.metadata();
        this.document = document;
        this.styleSheet = document.styleSheet();
        this.sequences = sequences;
        this.renderIds = PdfRenderIds.create(document);
        this.unitCount = unitCount;
    }

    /** @return 建立当前计划的完整文档模型 */
    DocumentModel document() {
        return document;
    }

    /** @return 完整文档元数据 */
    DocumentMetadata metadata() {
        return metadata;
    }

    /** @return 完整文档命名样式 */
    StyleSheet styleSheet() {
        return styleSheet;
    }

    /** @return 按原文档顺序保存的页面序列计划 */
    List<PdfSequencePlan> sequences() {
        return sequences;
    }

    /** @return 跨页面序列统一分配的布局 ID */
    PdfRenderIds renderIds() {
        return renderIds;
    }

    /** @return 全部页面序列的排版单元总量 */
    int unitCount() {
        return unitCount;
    }
}
