package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageNumbering;
import io.github.leylaragg.letool.print.document.PageRegion;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 文档计划对完整页面序列的快照和切分测试。
 *
 * @author leyland
 */
class PdfDocumentPlanTest {

    /** 每个页面序列都保持原顺序和自己的版式语义。 */
    @Test
    void shouldKeepEveryPageSequenceInDocumentOrder() {
        PageSequence first = sequence(List.of(paragraph("first")), PageNumbering.excluded());
        PageSequence second = sequence(List.of(paragraph("second")), PageNumbering.countedFrom(5));
        PageSequence third = sequence(List.of(paragraph("third")), PageNumbering.counted());
        DocumentModel document = document(List.of(first, second, third));

        PdfDocumentPlan plan = PdfDocumentPlan.create(document);

        assertThat(plan.sequences()).extracting(PdfSequencePlan::sourceIndex)
                .containsExactly(0, 1, 2);
        assertThat(plan.sequences()).extracting(PdfSequencePlan::pageLayout)
                .containsExactly(first.pageLayout(), second.pageLayout(), third.pageLayout());
        assertThat(plan.sequences()).extracting(PdfSequencePlan::pageNumbering)
                .containsExactly(first.pageNumbering(), second.pageNumbering(), third.pageNumbering());
    }

    /** 空正文仍保留一个空排版单元，不能让整个页面序列从输出中消失。 */
    @Test
    void shouldKeepEmptySequenceAsRenderUnit() {
        PdfSequencePlan sequence = PdfDocumentPlan.create(document(List.of(
                sequence(List.of(), PageNumbering.counted())))).sequences().get(0);

        assertThat(sequence.units()).singleElement()
                .satisfies(unit -> {
                    assertThat(unit.kind()).isEqualTo(PdfSequenceUnit.Kind.BODY);
                    assertThat(unit.blocks()).isEmpty();
                });
    }

    /** 目录和有效根分页只切分所在序列，嵌套分页继续交给排版器。 */
    @Test
    void shouldSplitOnlyCurrentSequenceBody() {
        HeadingNode heading = new HeadingNode("heading", 1, List.of(new TextNode("heading")));
        PageSequence first = sequence(List.of(
                paragraph("cover"),
                new TableOfContentsNode(null, 1, 3),
                paragraph("chapter"),
                PageBreakNode.INSTANCE,
                paragraph("appendix")), PageNumbering.counted());
        PageSequence second = sequence(List.of(heading), PageNumbering.counted());

        PdfDocumentPlan plan = PdfDocumentPlan.create(document(List.of(first, second)));
        PdfSequencePlan firstPlan = plan.sequences().get(0);

        assertThat(firstPlan.units()).extracting(PdfSequenceUnit::kind)
                .containsExactly(PdfSequenceUnit.Kind.BODY, PdfSequenceUnit.Kind.TOC,
                        PdfSequenceUnit.Kind.BODY, PdfSequenceUnit.Kind.BODY);
        assertThat(firstPlan.tableOfContentsIndex()).isEqualTo(1);
        assertThat(firstPlan.bodyUnitCount()).isEqualTo(3);
        assertThat(plan.sequences().get(1).units()).singleElement()
                .satisfies(unit -> assertThat(unit.blocks()).containsExactly(heading));
    }

    /** 序列末尾分页不会因为相邻序列有正文而被误当成当前序列边界。 */
    @Test
    void shouldNotReadAdjacentSequenceWhenSplittingRootBreaks() {
        PageSequence first = sequence(List.of(
                new TableOfContentsNode(null, 1, 1), PageBreakNode.INSTANCE),
                PageNumbering.excluded());
        PageSequence second = sequence(List.of(
                new HeadingNode("next", 1, List.of(new TextNode("next")))),
                PageNumbering.counted());

        PdfSequencePlan plan = PdfDocumentPlan.create(
                document(List.of(first, second))).sequences().get(0);

        assertThat(plan.units()).hasSize(2);
        assertThat(plan.units().get(1).blocks()).containsExactly(PageBreakNode.INSTANCE);
    }

    /** 自动标题 ID 在文档级分配，跨序列仍稳定且不冲突。 */
    @Test
    void shouldAllocateStableIdsAcrossSequences() {
        HeadingNode first = new HeadingNode("", 1, List.of(new TextNode("first")));
        HeadingNode second = new HeadingNode("", 1, List.of(new TextNode("second")));
        DocumentModel document = document(List.of(
                sequence(List.of(first), PageNumbering.counted()),
                sequence(List.of(second), PageNumbering.counted())));

        PdfRenderIds ids = PdfDocumentPlan.create(document).renderIds();

        assertThat(ids.targetId(first)).isEqualTo("letool-toc-heading-1");
        assertThat(ids.targetId(second)).isEqualTo("letool-toc-heading-2");
    }

    /** 排版单元总量恰好到上限可用，首次越界立即失败。 */
    @Test
    void shouldEnforceDocumentRenderUnitLimit() {
        assertThat(PdfDocumentPlan.create(document(List.of(
                sequence(blocksForUnits(1_000), PageNumbering.counted())))).unitCount())
                .isEqualTo(1_000);
        assertThatThrownBy(() -> PdfDocumentPlan.create(document(List.of(
                sequence(blocksForUnits(1_001), PageNumbering.counted())))))
                .hasMessageContaining("1,000");
    }

    /** 用根分页构造指定数量的非空正文单元。 */
    private static List<BlockNode> blocksForUnits(int count) {
        List<BlockNode> blocks = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                blocks.add(PageBreakNode.INSTANCE);
            }
            blocks.add(paragraph("p" + index));
        }
        return blocks;
    }

    /** 创建一个带页眉页脚的测试序列，便于核对快照边界。 */
    private static PageSequence sequence(List<BlockNode> body, PageNumbering numbering) {
        return new PageSequence(PageLayout.a4Portrait(),
                new PageRegion(List.of(paragraph("header"))),
                new PageRegion(List.of(paragraph("footer"))), numbering, body);
    }

    /** 创建多页面序列测试文档。 */
    private static DocumentModel document(List<PageSequence> sequences) {
        return new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), sequences);
    }

    /** 创建普通文本段落。 */
    private static ParagraphNode paragraph(String text) {
        return new ParagraphNode("", List.of(new TextNode(text)));
    }
}
