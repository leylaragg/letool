package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.render.RenderedDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 目录独占分页、页码收敛和可点击链接的真实 PDF 测试。
 *
 * @author leyland
 */
class PdfTableOfContentsTest {

    /** 封面后的目录独占一页，并显示标题最终所在的一基页码。 */
    @Test
    void shouldRenderConvergedLinkedTableOfContents() throws Exception {
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new ParagraphNode("", List.of(new TextNode("Cover"))),
                        new TableOfContentsNode("Contents", 1, 2),
                        new HeadingNode("", 1, List.of(new TextNode("Chapter One"))),
                        new ParagraphNode("", List.of(new TextNode("Body")))));

        RenderedDocument rendered = new PdfDocumentRenderer(List.of())
                .render(document, RenderOptions.defaults());

        try (PDDocument pdf = Loader.loadPDF(rendered.content())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertThat(text).contains("Contents", "Chapter One", "3");
            assertThat(pdf.getPage(1).getAnnotations())
                    .anyMatch(PDAnnotationLink.class::isInstance);
        }
    }

    /** 目录条目恰好达到上限可收集，首次越界在排版前失败。 */
    @Test
    void shouldEnforceTableOfContentsEntryLimit() {
        DocumentModel maximum = documentWithHeadings(PdfTableOfContentsComposer.MAX_ENTRIES);
        PdfRenderIds maximumIds = PdfRenderIds.create(maximum);

        assertThat(new PdfTableOfContentsComposer().collect(maximum, maximumIds))
                .hasSize(PdfTableOfContentsComposer.MAX_ENTRIES);

        DocumentModel overflow = documentWithHeadings(PdfTableOfContentsComposer.MAX_ENTRIES + 1);
        PdfRenderIds overflowIds = PdfRenderIds.create(overflow);
        assertThatThrownBy(() -> new PdfTableOfContentsComposer().collect(overflow, overflowIds))
                .hasMessageContaining("10,000");
    }

    /** 构造目录声明之后包含指定数量标题的文档。 */
    private static DocumentModel documentWithHeadings(int count) {
        List<BlockNode> blocks = new ArrayList<>(count + 1);
        blocks.add(new TableOfContentsNode(null, 1, 1));
        for (int index = 0; index < count; index++) {
            blocks.add(new HeadingNode("", 1, List.of(new TextNode("Heading " + index))));
        }
        return new DocumentModel(DocumentMetadata.empty(), PageLayout.a4Portrait(), blocks);
    }
}
