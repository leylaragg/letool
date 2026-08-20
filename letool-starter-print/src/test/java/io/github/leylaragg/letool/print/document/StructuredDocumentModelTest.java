package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.PageCountNode;
import io.github.leylaragg.letool.print.document.node.PageNumberNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TableLayoutMode;
import io.github.leylaragg.letool.print.document.style.TableStyle;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 多页面序列、重复区域和样式引用的整体验证测试。
 *
 * @author leyland
 */
class StructuredDocumentModelTest {

    /** 验证遍历按每个序列的页眉、正文、页脚顺序覆盖完整文档。 */
    @Test
    void shouldTraverseAllPageSequenceAreasInReadingOrder() {
        PageSequence first = sequence(
                new PageRegion(List.of(paragraph("页眉一"))),
                new PageRegion(List.of(paragraph("页脚一"))),
                PageNumbering.excluded(),
                List.of(paragraph("封面")));
        PageSequence second = sequence(
                new PageRegion(List.of(paragraph("页眉二"))),
                new PageRegion(List.of(paragraph("页脚二"))),
                PageNumbering.countedFrom(1),
                List.of(paragraph("正文")));

        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(), StyleSheet.empty(), List.of(first, second));

        assertThat(DocumentTraversal.depthFirst(document))
                .filteredOn(ParagraphNode.class::isInstance)
                .map(ParagraphNode.class::cast)
                .map(paragraph -> ((TextNode) paragraph.children().get(0)).text())
                .containsExactly("页眉一", "封面", "页脚一", "页眉二", "正文", "页脚二");
    }

    /** 验证重复区域拒绝导航目标和会改变正文流的节点。 */
    @Test
    void shouldRejectUnsafePageRegionContent() {
        assertThatThrownBy(() -> document(new PageRegion(List.of(
                new ParagraphNode("header-id", "", List.of(new TextNode("页眉", ""))))),
                PageNumbering.counted(), List.of(paragraph("正文"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("逻辑 ID");
        assertThatThrownBy(() -> document(new PageRegion(List.of(
                new SectionNode("", List.of(new HeadingNode(
                        "", 1, "", List.of(new TextNode("标题", ""))))))),
                PageNumbering.counted(), List.of(paragraph("正文"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("HeadingNode");
    }

    /** 验证未计入页码的序列不能显示当前页，但仍能显示文档总页数。 */
    @Test
    void shouldValidateLogicalPageNodesWithinSequence() {
        assertThatThrownBy(() -> document(PageRegion.empty(), PageNumbering.excluded(),
                List.of(new ParagraphNode("", "", List.of(new PageNumberNode(""))))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("当前页码");

        DocumentModel valid = document(PageRegion.empty(), PageNumbering.excluded(),
                List.of(new ParagraphNode("", "", List.of(new PageCountNode("")))));
        assertThat(valid.pageSequences()).hasSize(1);
    }

    /** 验证节点样式引用和表格结构由文档快照统一核对。 */
    @Test
    void shouldResolveTypedStylesAgainstDocumentNodes() {
        StyleSheet styles = StyleSheet.builder()
                .text("body-text", TextStyle.defaults())
                .paragraph("body", ParagraphStyle.builder().textStyleName("body-text").build())
                .table("grid", TableStyle.builder()
                        .layoutMode(TableLayoutMode.FIXED)
                        .columnWidths(List.of(
                                DocumentLength.percent(50), DocumentLength.percent(50)))
                        .repeatHeader(true)
                        .build())
                .build();
        TableNode table = new TableNode("", "grid", 1, List.of(new TableRow(List.of(
                new TableCell("", List.of(), 1, 1),
                new TableCell("", List.of(), 1, 1)))));

        DocumentModel document = new DocumentModel(DocumentMetadata.empty(), styles,
                List.of(PageSequence.body(PageLayout.a4Portrait(), List.of(
                        new ParagraphNode("", "body", List.of(new TextNode("正文", "body-text"))),
                        table))));

        assertThat(document.styleSheet()).isSameAs(styles);
        assertThatThrownBy(() -> new DocumentModel(DocumentMetadata.empty(), styles,
                List.of(PageSequence.body(PageLayout.a4Portrait(), List.of(
                        new ParagraphNode("", "missing", List.of()))))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("missing");
    }

    /** 创建带页眉和页码规则的单序列文档。 */
    private static DocumentModel document(
            PageRegion header, PageNumbering numbering, List<BlockNode> body) {
        return new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(),
                List.of(sequence(header, PageRegion.empty(), numbering, body)));
    }

    /** 创建页面序列。 */
    private static PageSequence sequence(PageRegion header, PageRegion footer,
            PageNumbering numbering, List<BlockNode> body) {
        return new PageSequence(PageLayout.a4Portrait(), header, footer, numbering, body);
    }

    /** 创建不带样式的普通段落。 */
    private static ParagraphNode paragraph(String text) {
        return new ParagraphNode("", "", List.of(new TextNode(text, "")));
    }
}
