package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 根节点排版单元的切分规则测试。
 *
 * @author leyland
 */
class PdfRenderPlanTest {

    /** 目录独占单元，根分页切开正文，章节内部的分页仍交给排版器处理。 */
    @Test
    void shouldCreateOrderedRenderUnits() {
        SectionNode nested = new SectionNode("chapter", List.of(
                new HeadingNode("chapter-title", 1, List.of(new TextNode("章节一"))),
                PageBreakNode.INSTANCE,
                paragraph("章节二")));
        DocumentModel document = document(List.of(
                paragraph("封面"),
                new TableOfContentsNode(null, 1, 3),
                nested,
                PageBreakNode.INSTANCE,
                paragraph("附录")));

        PdfRenderPlan plan = PdfRenderPlan.create(document);

        assertThat(plan.units()).extracting(PdfRenderUnit::kind)
                .containsExactly(PdfRenderUnit.Kind.BODY, PdfRenderUnit.Kind.TOC,
                        PdfRenderUnit.Kind.BODY, PdfRenderUnit.Kind.BODY);
        assertThat(plan.tableOfContentsIndex()).isEqualTo(1);
        assertThat(plan.bodyUnitCount()).isEqualTo(3);
        assertThat(((SectionNode) plan.units().get(2).blocks().get(0)).children())
                .contains(PageBreakNode.INSTANCE);
    }

    /** 无法形成有效左右正文的分页不能被边界算法静默吞掉。 */
    @Test
    void shouldKeepLeadingTrailingAndConsecutiveBreaks() {
        PdfRenderPlan plan = PdfRenderPlan.create(document(List.of(
                PageBreakNode.INSTANCE,
                paragraph("正文一"),
                PageBreakNode.INSTANCE,
                PageBreakNode.INSTANCE,
                paragraph("正文二"),
                PageBreakNode.INSTANCE)));

        assertThat(plan.units()).hasSize(2);
        assertThat(plan.units().get(0).blocks()).containsExactly(
                PageBreakNode.INSTANCE, paragraph("正文一"));
        assertThat(plan.units().get(1).blocks()).containsExactly(
                PageBreakNode.INSTANCE, paragraph("正文二"), PageBreakNode.INSTANCE);
    }

    /** 排版单元数量在上限内可用，首次越界立即失败。 */
    @Test
    void shouldEnforceRenderUnitLimit() {
        assertThat(PdfRenderPlan.create(document(blocksForUnits(1_000))).units()).hasSize(1_000);
        assertThatThrownBy(() -> PdfRenderPlan.create(document(blocksForUnits(1_001))))
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

    /** 创建测试正文。 */
    private static ParagraphNode paragraph(String text) {
        return new ParagraphNode("", List.of(new TextNode(text)));
    }

    /** 创建固定页面布局的测试文档。 */
    private static DocumentModel document(List<BlockNode> blocks) {
        return DocumentModel.singleSequence(DocumentMetadata.empty(), PageLayout.a4Portrait(), blocks);
    }
}
