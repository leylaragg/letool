package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageNumbering;
import io.github.leylaragg.letool.print.document.PageRegion;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.PageCountNode;
import io.github.leylaragg.letool.print.document.node.PageNumberNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 重复页眉页脚、逻辑页码和区域高度测试。
 *
 * @author leyland
 */
class PdfPageRegionTest {

    /** 页眉页脚使用 running element，页码和总页数来自当前分页计划。 */
    @Test
    void shouldRenderRunningRegionsAndLogicalPageValues() {
        PageRegion header = new PageRegion(List.of(new ParagraphNode("", List.of(
                new TextNode("Page "), new PageNumberNode("")))));
        PageRegion footer = new PageRegion(List.of(new ParagraphNode("", List.of(
                new TextNode("Total "), new PageCountNode("")))));
        PageSequence source = new PageSequence(PageLayout.a4Portrait(), header, footer,
                PageNumbering.countedFrom(7), List.of(new ParagraphNode(
                        "", List.of(new TextNode("body")))));
        PdfDocumentPlan document = PdfDocumentPlan.create(new DocumentModel(
                DocumentMetadata.empty(), StyleSheet.empty(), List.of(source)));
        PdfSequencePlan sequence = document.sequences().get(0);
        PdfPaginationPlan pagination = new PdfPaginationPlanner(5)
                .next(document, List.of(3));

        String xhtml = new PdfXhtmlRenderer(PdfFontCatalog.of(List.of()))
                .render(document, sequence, sequence.units().get(0).blocks(),
                        pagination, document.renderIds(), true);

        assertThat(xhtml)
                .contains("@top-center{content:element(lt-page-header);}")
                .contains("@bottom-center{content:element(lt-page-footer);}")
                .contains("position:running(lt-page-header)")
                .contains("position:running(lt-page-footer)")
                .contains("class=\"lt-page-number\"")
                .contains(".lt-page-number:before{content:counter(page);}")
                .contains("Total 3");
        assertThat(pagination.sequence(0).initialPageNumber()).isEqualTo(7);
    }

    /** 排除序列不产生页码占位，但仍能显示文档逻辑总页数。 */
    @Test
    void shouldHidePageNumberForExcludedSequence() {
        PageRegion header = new PageRegion(List.of(new ParagraphNode("", List.of(
                new TextNode("Total "), new PageCountNode("")))));
        PageSequence source = new PageSequence(PageLayout.a4Portrait(), header,
                PageRegion.empty(), PageNumbering.excluded(), List.of());
        PdfDocumentPlan document = PdfDocumentPlan.create(new DocumentModel(
                DocumentMetadata.empty(), StyleSheet.empty(), List.of(source)));
        PdfPaginationPlan pagination = new PdfPaginationPlanner(5)
                .next(document, List.of(1));

        String xhtml = new PdfXhtmlRenderer(PdfFontCatalog.of(List.of()))
                .render(document, document.sequences().get(0), List.of(),
                        pagination, document.renderIds(), true);

        assertThat(xhtml).doesNotContain("class=\"lt-page-number\"").contains("Total 0");
    }

    /** 页面区域高度恰好落在边距内可用，首次越界立即失败。 */
    @Test
    void shouldRejectPageRegionOverflow() {
        PdfLayoutSnapshot withinMargin = snapshot(56F, 56F);
        PdfLayoutSnapshot overflow = snapshot(57F, 56F);

        assertThatCode(() -> withinMargin.requireRegionsFit(PageLayout.a4Portrait()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> overflow.requireRegionsFit(PageLayout.a4Portrait()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("页眉")
                .hasMessageNotContaining("57");
    }

    /** 创建只带区域测量值的布局快照。 */
    private static PdfLayoutSnapshot snapshot(float headerPoints, float footerPoints) {
        PdfLayoutSnapshot.Position position = new PdfLayoutSnapshot.Position(
                0, new PDRectangle(0, 0, 1, 1));
        return new PdfLayoutSnapshot(Map.of("target", position), Map.of(),
                headerPoints, footerPoints);
    }
}
