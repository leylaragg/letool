package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TextNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用文档模型到真实 PDF 产物的闭环测试。
 *
 * @author leyland
 */
class PdfDocumentRendererTest {

    /** 渲染结果可由 PDFBox 重新打开，并保留分页、文本、字体和导航语义。 */
    @Test
    void shouldRenderReadablePdfWithMetadataAndInternalLink() throws Exception {
        PdfDocumentRenderer renderer = renderer();
        DocumentModel document = new DocumentModel(
                new DocumentMetadata("阶段报告", "leyland", "zh-CN"),
                PageLayout.a4Portrait(),
                List.of(
                        new HeadingNode("summary-heading", 1, List.of(
                                new BookmarkNode("summary", "汇总书签"),
                                new TextNode("汇总 Summary"))),
                        new ParagraphNode("", List.of(new TextNode("中文正文 Hello PDF"))),
                        new ParagraphNode("", List.of(
                                new InternalLinkNode("summary", List.of(new TextNode("返回汇总"))))),
                        PageBreakNode.INSTANCE,
                        new ParagraphNode("", List.of(new TextNode("第二页内容")))));

        RenderedPdf rendered = RenderedPdf.render(
                renderer,
                document,
                new RenderOptions(10, 10L * 1024 * 1024, 30L * 1024 * 1024, true));

        assertThat(rendered.result().outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(rendered.content()).startsWith('%', 'P', 'D', 'F');
        assertThat(rendered.result().metadata()).containsEntry("pageCount", "2");
        assertThat(rendered.result().metadata()).containsKey("contentLength");
        try (PDDocument pdf = Loader.loadPDF(rendered.content())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo("阶段报告");
            assertThat(pdf.getDocumentInformation().getAuthor()).isEqualTo("leyland");
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("汇总书签汇总 Summary", "中文正文 Hello PDF", "第二页内容");
            assertThat(hasEmbeddedFont(pdf)).isTrue();
            assertThat(pageAnnotations(pdf.getPage(0)))
                    .anyMatch(PDAnnotationLink.class::isInstance);
            assertThat(pdf.getDocumentCatalog().getDocumentOutline().getFirstChild().getTitle())
                    .isEqualTo("汇总书签");
        }
    }

    /** 表头进入 thead 后会在排版产生的后续页面上重复。 */
    @Test
    void shouldRepeatTableHeaderAcrossPages() throws Exception {
        List<TableRow> rows = new ArrayList<>();
        rows.add(row("重复表头"));
        for (int index = 1; index <= 120; index++) {
            rows.add(row("数据行 " + index));
        }
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(new TableNode("records", 1, rows)));

        RenderedPdf rendered = RenderedPdf.render(renderer(), document, RenderOptions.defaults());

        try (PDDocument pdf = Loader.loadPDF(rendered.content())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(pdf)).contains("重复表头");
            }
        }
    }

    /** 宿主关闭文档元数据后，业务标题、作者和语言不会写入 PDF。 */
    @Test
    void shouldOmitDocumentMetadataWhenDisabled() throws Exception {
        DocumentModel document = new DocumentModel(
                new DocumentMetadata("secret-title", "secret-author", "zh-CN"),
                PageLayout.a4Portrait(),
                List.of(new ParagraphNode("", List.of(new TextNode("正文")))));

        RenderedPdf rendered = RenderedPdf.render(
                renderer(),
                document,
                new RenderOptions(10, 10L * 1024 * 1024, 30L * 1024 * 1024, false));

        try (PDDocument pdf = Loader.loadPDF(rendered.content())) {
            assertThat(pdf.getDocumentInformation().getTitle()).isNull();
            assertThat(pdf.getDocumentInformation().getAuthor()).isNull();
            assertThat(pdf.getDocumentCatalog().getLanguage()).isNull();
        }
    }

    /** 创建使用测试专用 Apache 2.0 字体的渲染器。 */
    private PdfDocumentRenderer renderer() {
        PdfFont font = new PdfFont("Droid Sans Fallback", this::openTestFont, true);
        return new PdfDocumentRenderer(List.of(font));
    }

    /** 每次渲染都从类路径重新打开字体流。 */
    private InputStream openTestFont() {
        return Objects.requireNonNull(
                getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"),
                "测试字体不存在");
    }

    /** 创建单列表格行。 */
    private TableRow row(String text) {
        ParagraphNode paragraph = new ParagraphNode("", List.of(new TextNode(text)));
        return new TableRow(List.of(new TableCell(List.of(paragraph), 1, 1)));
    }

    /** 检查至少一个页面字体已经嵌入产物。 */
    private boolean hasEmbeddedFont(PDDocument document) throws Exception {
        for (PDPage page : document.getPages()) {
            for (var name : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(name);
                if (font != null && font.isEmbedded()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 读取页面批注，避免测试依赖 PDFBox 的内部集合实现。 */
    private List<PDAnnotation> pageAnnotations(PDPage page) throws Exception {
        return page.getAnnotations();
    }
}
