package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.Margins;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageOrientation;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.PageSize;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证多页面序列经过统一装配后仍是一份语义完整的 PDF。
 *
 * @author leyland
 */
class PdfMixedPageSequenceTest {

    /** 页面边界不能割裂目录、导航、批注和文档元数据。 */
    @Test
    void shouldKeepMixedLayoutsAndCrossSequenceSemantics() throws Exception {
        DocumentModel document = new DocumentModel(
                new DocumentMetadata("混合页面报告", "leyland", "zh-CN"),
                StyleSheet.empty(),
                List.of(
                        PageSequence.body(PageLayout.a4Portrait(), List.of(
                                paragraph("封面"),
                                new ParagraphNode("", List.of(new InternalLinkNode(
                                        "appendix", List.of(new TextNode("查看附录"))))),
                                new TableOfContentsNode("目录", 1, 2))),
                        PageSequence.body(a4Landscape(), List.of(
                                new HeadingNode("chapter", 1, List.of(
                                        new BookmarkNode("chapter-bookmark", "第一章"))),
                                paragraph("横向正文"),
                                new AnnotationNode(AnnotationType.TEXT_NOTE, "chapter",
                                        AnnotationPlacement.TOP_RIGHT, 6_000, 6_000,
                                        0, 0, "reviewer", "请复核"))),
                        PageSequence.body(customPortrait(), List.of(
                                new HeadingNode("appendix", 2, List.of(new TextNode("附录"))),
                                paragraph("自定义页面正文"),
                                new AnnotationNode(AnnotationType.FREE_TEXT, "appendix",
                                        AnnotationPlacement.BOTTOM_RIGHT, 30_000, 15_000,
                                        0, 0, "reviewer", "最终说明")))));

        RenderedPdf rendered = RenderedPdf.render(renderer(), document, RenderOptions.defaults());

        try (PDDocument pdf = Loader.loadPDF(rendered.content())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(4);
            assertThat(pageSize(pdf, 0)).isNotEqualTo(pageSize(pdf, 2));
            assertThat(pageSize(pdf, 2)).isNotEqualTo(pageSize(pdf, 3));
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("封面", "目录", "第一章", "横向正文", "附录", "自定义页面正文");
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo("混合页面报告");
            assertThat(pdf.getPage(0).getAnnotations()).anyMatch(PDAnnotationLink.class::isInstance);
            assertThat(pdf.getPage(1).getAnnotations()).anyMatch(PDAnnotationLink.class::isInstance);
            assertThat(pdf.getPage(2).getAnnotations()).anyMatch(PDAnnotationText.class::isInstance);
            assertThat(pdf.getPage(3).getAnnotations()).anyMatch(PDAnnotationFreeText.class::isInstance);
            assertThat(pdf.getDocumentCatalog().getDocumentOutline()).isNotNull();
        }
    }

    /** A4 横向序列使用独立页面尺寸和边距。 */
    private PageLayout a4Landscape() {
        return new PageLayout(PageSize.A4, PageOrientation.LANDSCAPE,
                new Margins(20_000, 20_000, 20_000, 20_000));
    }

    /** 自定义页面用于证明合并不会把后续序列改回首个版式。 */
    private PageLayout customPortrait() {
        return new PageLayout(new PageSize(160_000, 240_000), PageOrientation.PORTRAIT,
                new Margins(15_000, 15_000, 15_000, 15_000));
    }

    /** 创建默认样式正文。 */
    private ParagraphNode paragraph(String text) {
        return new ParagraphNode("", List.of(new TextNode(text)));
    }

    /** 使用可覆盖中文的测试字体渲染自由文本批注。 */
    private OpenHtmlPdfRenderer renderer() {
        PdfFont font = new PdfFont(
                "Droid Sans Fallback", FontWeight.NORMAL, this::openTestFont, true);
        return new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(font)));
    }

    /** 每次渲染都重新打开类路径字体流。 */
    private InputStream openTestFont() {
        return Objects.requireNonNull(
                getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"),
                "测试字体不存在");
    }

    /** 把页面宽高转换为便于比较的稳定文本。 */
    private String pageSize(PDDocument document, int pageIndex) {
        PDRectangle box = document.getPage(pageIndex).getMediaBox();
        return Math.round(box.getWidth()) + "x" + Math.round(box.getHeight());
    }
}
