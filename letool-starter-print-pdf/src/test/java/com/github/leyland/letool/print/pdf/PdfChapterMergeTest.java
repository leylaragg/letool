package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.AnnotationNode;
import com.github.leyland.letool.print.document.node.AnnotationPlacement;
import com.github.leyland.letool.print.document.node.AnnotationType;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
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
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(), PageLayout.a4Portrait(), List.of(
                new HeadingNode("first", 1, List.of(new BookmarkNode("outline", "First"))),
                new ParagraphNode("", List.of(new InternalLinkNode(
                        "second", List.of(new TextNode("Next"))))),
                PageBreakNode.INSTANCE,
                new HeadingNode("second", 1, List.of(new TextNode("Second"))),
                new AnnotationNode(AnnotationType.TEXT_NOTE, "second",
                        AnnotationPlacement.TOP_RIGHT, 6_000, 6_000,
                        0, 0, "reviewer", "check")));

        byte[] content = new PdfDocumentRenderer(List.of())
                .render(document, RenderOptions.defaults()).content();

        try (PDDocument pdf = Loader.loadPDF(content)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            assertThat(pdf.getPage(0).getAnnotations()).anyMatch(PDAnnotationLink.class::isInstance);
            assertThat(pdf.getPage(1).getAnnotations()).anyMatch(PDAnnotationText.class::isInstance);
            assertThat(pdf.getDocumentCatalog().getDocumentOutline()).isNotNull();
        }
    }
}
