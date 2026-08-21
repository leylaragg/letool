package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.CellStyle;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TableLayoutMode;
import io.github.leylaragg.letool.print.document.style.TablePageBreakPolicy;
import io.github.leylaragg.letool.print.document.style.TableStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDF 固定表格结构和跨页策略降级测试。
 *
 * @author leyland
 */
class PdfTableLayoutTest {

    /** 固定列宽、重复表头、跨度和单元格样式都保留为结构化 XHTML。 */
    @Test
    void shouldRenderRepeatableHeaderAndFixedColumns() {
        StyleSheet styles = StyleSheet.builder()
                .table("grid", TableStyle.builder()
                        .layoutMode(TableLayoutMode.FIXED)
                        .columnWidths(List.of(
                                DocumentLength.percent(30), DocumentLength.percent(70)))
                        .repeatHeader(true)
                        .pageBreakPolicy(TablePageBreakPolicy.KEEP_ROWS)
                        .build())
                .cell("header", CellStyle.builder()
                        .background(DocumentColor.rgb(230, 230, 230)).build())
                .build();
        TableNode table = new TableNode("", "grid", 1, List.of(
                new TableRow(List.of(new TableCell(
                        "header", List.of(paragraph("header")), 1, 2))),
                new TableRow(List.of(
                        new TableCell(List.of(paragraph("left")), 1, 1),
                        new TableCell(List.of(paragraph("right")), 1, 1)))));
        DocumentModel document = new DocumentModel(DocumentMetadata.empty(), styles,
                List.of(PageSequence.body(PageLayout.a4Portrait(), List.of(table))));

        String xhtml = new PdfXhtmlRenderer(PdfFontCatalog.of(List.of())).render(document);

        assertThat(xhtml)
                .contains("<table class=\"lt-table-0\">")
                .contains("<colgroup><col style=\"width:30%;\"/>"
                        + "<col style=\"width:70%;\"/></colgroup>")
                .contains("<thead><tr><th class=\"lt-cell-0\" colspan=\"2\">")
                .contains("</thead><tbody>")
                .contains(".lt-table-0>thead{display:table-header-group;}")
                .contains(".lt-table-0 tr{page-break-inside:avoid;}");
    }

    /** 跨页策略只按 KEEP_TABLE、KEEP_ROWS、AUTO 的顺序降级。 */
    @Test
    void shouldDowngradePageBreakPolicyInOrder() {
        PdfTableLayoutPlanner planner = new PdfTableLayoutPlanner();

        assertThat(planner.resolve(TablePageBreakPolicy.KEEP_TABLE, true, true))
                .isEqualTo(TablePageBreakPolicy.KEEP_TABLE);
        assertThat(planner.resolve(TablePageBreakPolicy.KEEP_TABLE, false, true))
                .isEqualTo(TablePageBreakPolicy.KEEP_ROWS);
        assertThat(planner.resolve(TablePageBreakPolicy.KEEP_TABLE, false, false))
                .isEqualTo(TablePageBreakPolicy.AUTO);
        assertThat(planner.resolve(TablePageBreakPolicy.KEEP_ROWS, false, false))
                .isEqualTo(TablePageBreakPolicy.AUTO);
        assertThat(planner.resolve(TablePageBreakPolicy.AUTO, false, false))
                .isEqualTo(TablePageBreakPolicy.AUTO);
    }

    /** 创建普通表格单元格段落。 */
    private static ParagraphNode paragraph(String text) {
        return new ParagraphNode("", List.of(new TextNode(text)));
    }
}
