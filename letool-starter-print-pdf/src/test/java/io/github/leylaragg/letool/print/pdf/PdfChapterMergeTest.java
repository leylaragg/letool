package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合并后跨章节导航、书签和批注的真实 PDF 测试。
 *
 * @author leyland
 */
class PdfChapterMergeTest {

    /** 根分页切开的两个单元仍共享最终目标坐标。 */
    @Test
    void shouldRepairCrossChapterNavigationAfterMerge() throws Exception {
        DocumentModel document = DocumentModel.singleSequence(
                DocumentMetadata.empty(), PageLayout.a4Portrait(), List.of(
                new HeadingNode("first", 1, List.of(new BookmarkNode("outline", "First"))),
                new ParagraphNode("", List.of(new InternalLinkNode(
                        "second", List.of(new TextNode("Next"))))),
                PageBreakNode.INSTANCE,
                new HeadingNode("second", 1, List.of(new TextNode("Second"))),
                new AnnotationNode(AnnotationType.TEXT_NOTE, "second",
                        AnnotationPlacement.TOP_RIGHT, 6_000, 6_000,
                        0, 0, "reviewer", "check")));

        byte[] content = RenderedPdf.render(
                new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of())),
                document, RenderOptions.defaults()).content();

        try (PDDocument pdf = Loader.loadPDF(content)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            assertThat(pdf.getPage(0).getAnnotations()).anyMatch(PDAnnotationLink.class::isInstance);
            assertThat(pdf.getPage(1).getAnnotations()).anyMatch(PDAnnotationText.class::isInstance);
            assertThat(pdf.getDocumentCatalog().getDocumentOutline()).isNotNull();
        }
    }
}
