package io.github.leylaragg.letool.print.pdf.internal.layout;

import io.github.leylaragg.letool.print.pdf.OpenHtmlPdfRenderer;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import io.github.leylaragg.letool.print.pdf.testsupport.RenderedPdf;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsLayout;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证原生拆页和实测目录在 Java 17 整理后保留完整输出。 */
class PdfNativeLayoutRegressionTest {

    @Test
    void shouldRepeatTheLabelAndKeepEveryInnerRowAcrossPages() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            rows.append("<tr><td>item-").append(String.format("%03d", index)).append("</td></tr>");
        }
        String fragmented = fragment(table(rows.toString()));

        // 通过最终 PDF 检查实际页数、重复侧和全部内容，避免只验证生成的 HTML。
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PdfBoxRenderer renderer = renderer(fragmented, output)) {
            renderer.layout();
            renderer.createPDF();
        }
        try (PDDocument pdf = Loader.loadPDF(output.toByteArray())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            String allText = stripper.getText(pdf);
            for (int index = 0; index < 40; index++) {
                String token = "item-" + String.format("%03d", index);
                assertThat(allText).containsOnlyOnce(token);
            }
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(pdf);
                if (pageText.contains("item-")) {
                    assertThat(pageText).contains("Repeat label");
                }
            }
        }
    }

    /** 已测量的行变成独立段落后，不应再次携带原始 br 造成双倍行高。 */
    @Test
    void shouldNotRepeatExplicitBreakInsideMeasuredParagraphLines() throws Exception {
        StringBuilder content = new StringBuilder("<p>");
        for (int index = 0; index < 60; index++) {
            content.append("line-").append(index).append("<br/>");
        }
        content.append("</p>");
        String xhtml = table("").replace("<table><tbody></tbody></table>", content.toString());
        assertThat(fragment(xhtml)).doesNotContain("<br");
    }

    @Test
    void shouldLeaveShortRowsUntouchedAndRejectAnIndivisibleOversizedBlock() throws Exception {
        String shortRow = table("<tr><td>short</td></tr>");
        assertThat(fragment(shortRow)).isEqualTo(shortRow);
        assertThatThrownBy(() -> fragment(table("<tr><td style=\"height:500pt\">oversized</td></tr>")))
                .isInstanceOf(IOException.class).hasMessageContaining("完整");
    }

    @Test
    void shouldRenderMeasuredContentsWithEntriesAndWithAnEmptyRange() throws Exception {
        for (boolean empty : List.of(false, true)) {
            TableOfContentsLayout layout = new TableOfContentsLayout(
                    80, 110, 60, 24, 20, 16, 12, 4, 4, 1, 0.5, 1, 2, 4, 0.3,
                    DocumentColor.rgb(100, 100, 100), "");
            TableOfContentsNode contents = new TableOfContentsNode(
                    "contents", "Contents", 1, 2, "root", "", layout, "Empty range");
            ParagraphNode body = new ParagraphNode("body", empty
                    ? List.of(new TextNode("Body"), new BookmarkNode("root", "Root", "body", "", true))
                    : List.of(new TextNode("Body"), new BookmarkNode("root", "Root", "body", "", true),
                            new BookmarkNode("entry", "Entry label", "body", "root", true)));
            DocumentModel document = new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), List.of(
                    PageSequence.body(PageLayout.a4Portrait(), List.of(contents)),
                    PageSequence.body(PageLayout.a4Portrait(), List.of(body))));

            RenderedPdf rendered = RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of())),
                    document, new RenderOptions(10, 1024 * 1024, 4 * 1024 * 1024, false));
            try (PDDocument pdf = Loader.loadPDF(rendered.content())) {
                assertThat(pdf.getNumberOfPages()).isEqualTo(2);
                assertThat(new PDFTextStripper().getText(pdf))
                        .contains("Contents", "Body", empty ? "Empty range" : "Entry label");
                assertThat(pdf.getPage(0).getAnnotations()).hasSize(empty ? 0 : 1);
            }
        }
    }

    /** 小页面便于在少量完整内层行上覆盖跨页边界。 */
    private static String table(String rows) {
        return "<html><head><style>@page{size:300pt 250pt;margin:20pt;}"
                + "body{font-family:serif;font-size:10pt;}table{width:100%;border-collapse:collapse;}"
                + "td{padding:2pt;}p{margin:0;}tr{page-break-inside:avoid;}"
                + "</style></head><body><table><tbody><tr>"
                + "<td data-repeat-on-split=\"true\"><p>Repeat label</p></td>"
                + "<td><table><tbody>" + rows + "</tbody></table></td>"
                + "</tr></tbody></table></body></html>";
    }

    private static String fragment(String xhtml) throws IOException {
        try (PdfBoxRenderer renderer = renderer(xhtml, OutputStream.nullOutputStream())) {
            renderer.layout();
            return PdfNativeFragmenter.fragment(renderer, xhtml);
        }
    }

    private static PdfBoxRenderer renderer(String xhtml, OutputStream output) throws IOException {
        return new PdfRendererBuilder().withHtmlContent(xhtml, null).toStream(output).buildPdfRenderer();
    }
}
