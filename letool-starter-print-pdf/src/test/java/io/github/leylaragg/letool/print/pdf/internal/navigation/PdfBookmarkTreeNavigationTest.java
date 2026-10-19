package io.github.leylaragg.letool.print.pdf.internal.navigation;

import io.github.leylaragg.letool.print.pdf.OpenHtmlPdfRenderer;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import io.github.leylaragg.letool.print.pdf.internal.navigation.PdfRenderIds;
import io.github.leylaragg.letool.print.pdf.internal.navigation.PdfTableOfContentsComposer;
import io.github.leylaragg.letool.print.pdf.internal.navigation.PdfTocEntry;
import io.github.leylaragg.letool.print.pdf.testsupport.RenderedPdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentNavigationOptions;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PageMode;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用 PDF reader 验证树、初始视图和目录的真实物理目的地。 */
class PdfBookmarkTreeNavigationTest {

    @Test
    void shouldRenderCollapsedDualTreesAndPageTopDestinations() throws Exception {
        try (PDDocument pdf = render(document(false, false))) {
            List<PDOutlineItem> roots = children(pdf.getDocumentCatalog().getDocumentOutline().children());
            assertThat(roots).extracting(PDOutlineItem::getTitle)
                    .containsExactly("Cover", "Contents", "By Visit", "By Form");
            PDOutlineItem visits = roots.get(2);
            PDOutlineItem forms = roots.get(3);
            assertThat(visits.getCOSObject().getInt(COSName.COUNT)).isNegative();
            assertThat(forms.getCOSObject().getInt(COSName.COUNT)).isNegative();
            assertThat(children(visits.children())).extracting(PDOutlineItem::getTitle)
                    .containsExactly("Same Visit", "Same Visit");
            assertThat(children(forms.children())).extracting(PDOutlineItem::getTitle)
                    .containsExactly("Same Form", "Same Form");
            PDOutlineItem visitOccurrence = visits.getFirstChild().getFirstChild();
            PDOutlineItem formOccurrence = forms.getFirstChild().getFirstChild();
            assertThat(visitOccurrence.findDestinationPage(pdf)).isEqualTo(formOccurrence.findDestinationPage(pdf));
            assertTop(pdf, (PDPageXYZDestination) visitOccurrence.getDestination());
            assertThat(pdf.getDocumentCatalog().getPageMode()).isEqualTo(PageMode.USE_OUTLINES);
            assertThat(pdf.getDocumentCatalog().getCOSObject().containsKey(COSName.PAGE_LAYOUT)).isFalse();
            PDPageXYZDestination open = (PDPageXYZDestination) pdf.getDocumentCatalog().getOpenAction();
            assertThat(pdf.getPages().indexOf(open.getPage())).isZero();
            assertTop(pdf, open);
            assertThat(pageText(pdf, 1)).contains("Cover body").doesNotContain("By Visit", "Same Visit", "Same Form");
            for (org.apache.pdfbox.pdmodel.PDPage page : pdf.getPages()) {
                for (org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link) {
                        assertTop(pdf, (PDPageXYZDestination) link.getDestination());
                    }
                }
            }
        }
    }

    @Test
    void shouldCollectBookmarkDescendantsInTreeOrderAndMoveTheirPhysicalPages() throws Exception {
        DocumentModel document = document(false, false);
        List<PdfTocEntry> entries = new PdfTableOfContentsComposer().collect(document, PdfRenderIds.create(document));
        assertThat(entries).extracting(PdfTocEntry::title)
                .containsExactly("Same Visit", "Occurrence One", "Same Visit", "Occurrence Two");
        assertThat(entries).extracting(PdfTocEntry::targetId)
                .containsExactly("form-1", "form-1", "form-2", "form-2");
        try (PDDocument original = render(document); PDDocument moved = render(document(true, false))) {
            int originalFirst = targetPage(original, "Occurrence One");
            int originalSecond = targetPage(original, "Occurrence Two");
            assertThat(targetPage(moved, "Occurrence One")).isEqualTo(originalFirst + 1);
            assertThat(targetPage(moved, "Occurrence Two")).isEqualTo(originalSecond + 1);
            assertContentsLinks(original, originalFirst, originalSecond);
            assertContentsLinks(moved, originalFirst + 1, originalSecond + 1);
        }
    }

    @Test
    void shouldRenderFourRootsAndAnEmptyBookmarkContents() throws Exception {
        DocumentModel document = document(false, true);
        assertThat(new PdfTableOfContentsComposer().collect(document, PdfRenderIds.create(document))).isEmpty();
        try (PDDocument pdf = render(document)) {
            List<PDOutlineItem> roots = children(pdf.getDocumentCatalog().getDocumentOutline().children());
            assertThat(roots).hasSize(4);
            assertThat(roots.get(2).getFirstChild()).isNull();
            assertThat(roots.get(3).getFirstChild()).isNull();
            assertThat(roots.get(2).findDestinationPage(pdf)).isEqualTo(roots.get(1).findDestinationPage(pdf));
            assertThat(new PDFTextStripper().getText(pdf)).contains("Contents title", "Empty range");
        }
    }

    @Test
    void shouldFilterBookmarkContentsByRelativeDescendantLevel() {
        DocumentModel document = DocumentModel.singleSequence(DocumentMetadata.empty(), PageLayout.a4Portrait(),
                List.of(new TableOfContentsNode("Contents", 2, 2, "root"), new ParagraphNode("body", List.of(
                        new TextNode("Actual body"), bookmark("level-3", "Level Three", "body", "level-2"),
                        bookmark("level-2", "Level Two", "body", "level-1"),
                        bookmark("level-1", "Level One", "body", "root"),
                        bookmark("root", "Root", "body", "")))));
        List<PdfTocEntry> entries = new PdfTableOfContentsComposer().collect(document, PdfRenderIds.create(document));
        assertThat(entries).extracting(PdfTocEntry::title).containsExactly("Level Two");
        assertThat(entries).extracting(PdfTocEntry::level).containsExactly(2);
    }

    @Test
    void shouldWriteAnnotationAtTheMetadataAliasesActualTarget() throws Exception {
        DocumentModel document = new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), List.of(
                PageSequence.body(PageLayout.a4Portrait(), List.of(new ParagraphNode("cover", List.of(
                        new TextNode("Cover"), bookmark("alias", "Alias", "body", ""),
                        bookmark("nested-alias", "Nested alias", "alias", ""))))),
                PageSequence.body(PageLayout.a4Portrait(), List.of(paragraph("body", "Actual body"),
                        new AnnotationNode(AnnotationType.TEXT_NOTE, "nested-alias", AnnotationPlacement.TOP_LEFT,
                                6_000, 6_000, 0, 0, "", "Alias annotation")))));
        try (PDDocument pdf = render(document)) {
            assertThat(pdf.getPage(0).getAnnotations()).isEmpty();
            assertThat(pdf.getPage(1).getAnnotations()).singleElement().satisfies(annotation -> {
                assertThat(annotation).isInstanceOf(org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
                    .class);
                assertThat(annotation.getContents()).isEqualTo("Alias annotation");
                assertThat(annotation.getRectangle().getLowerLeftY()).isNotNegative();
            });
        }
    }

    @Test
    void shouldResolveMetadataLinkAndInitialTargetsToActualContent() throws Exception {
        DocumentModel document = new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), List.of(
                PageSequence.body(PageLayout.a4Portrait(), List.of(new ParagraphNode("form", List.of(
                        new TextNode("Actual body"), bookmark("entry", "Metadata title", "form", ""),
                        new InternalLinkNode("entry", List.of(new TextNode("Open entry")))))))),
                DocumentOptions.builder().navigation(DocumentNavigationOptions.builder()
                        .destinationMode(DocumentNavigationOptions.DestinationMode.PAGE_TOP)
                        .initialTarget("entry").build()).build());
        PdfRenderIds ids = PdfRenderIds.create(document);
        assertThat(ids.targetIds()).contains("form").doesNotContain("entry");
        assertThat(ids.targetId("entry")).isEqualTo("form");
        try (PDDocument pdf = render(document)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Actual body").doesNotContain("Metadata title");
            assertTop(pdf, (PDPageXYZDestination) pdf.getDocumentCatalog().getOpenAction());
            PDAnnotationLink link = (PDAnnotationLink) pdf.getPage(0).getAnnotations().get(0);
            assertThat(((PDPageXYZDestination) link.getDestination()).getPage()).isEqualTo(pdf.getPage(0));
            assertThat(pdf.getDocumentCatalog().getDocumentOutline().getFirstChild().findDestinationPage(pdf))
                    .isEqualTo(pdf.getPage(0));
        }
    }

    @Test
    void shouldRejectSequenceOverflowBeforeOpeningTheRenderer() {
        List<PageSequence> sequences = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            sequences.add(PageSequence.body(PageLayout.a4Portrait(), List.of(paragraph("", "Body"))));
        }
        DocumentModel document = new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), sequences);
        OpenHtmlPdfRenderer renderer = new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of()),
                java.nio.file.Path.of("Z:\\nonexistent-sdk-test-root"));
        assertThatThrownBy(() -> RenderedPdf.render(renderer, document, new RenderOptions(256,
                RenderOptions.DEFAULT_MAX_OUTPUT_BYTES, RenderOptions.DEFAULT_MAX_TEMPORARY_BYTES, true)))
                .isInstanceOf(PrintRenderingException.class).hasMessageContaining("256");
    }

    private DocumentModel document(boolean extraCoverPage, boolean empty) {
        List<InlineNode> metadata = new ArrayList<>();
        if (!empty) {
            metadata.add(bookmark("visit-occ-1", "Occurrence One", "form-1", "visit-1"));
            metadata.add(bookmark("form-occ-1", "Occurrence One", "form-1", "form-def-1"));
            metadata.add(bookmark("visit-occ-2", "Occurrence Two", "form-2", "visit-2"));
            metadata.add(bookmark("form-occ-2", "Occurrence Two", "form-2", "form-def-2"));
        }
        metadata.add(bookmark("cover-book", "Cover", "cover", ""));
        metadata.add(bookmark("contents-book", "Contents", "contents", ""));
        metadata.add(bookmark("by-visit", "By Visit", empty ? "contents" : "form-1", ""));
        metadata.add(bookmark("by-form", "By Form", empty ? "contents" : "form-1", ""));
        if (!empty) {
            metadata.add(bookmark("visit-1", "Same Visit", "form-1", "by-visit"));
            metadata.add(bookmark("visit-2", "Same Visit", "form-2", "by-visit"));
            metadata.add(bookmark("form-def-1", "Same Form", "form-1", "by-form"));
            metadata.add(bookmark("form-def-2", "Same Form", "form-2", "by-form"));
        }
        metadata.add(new TextNode("Cover body"));
        metadata.add(new InternalLinkNode("contents", List.of(new TextNode("Go contents"))));
        List<BlockNode> cover = new ArrayList<>(List.of(new ParagraphNode("cover", metadata)));
        if (extraCoverPage) {
            cover.add(PageBreakNode.INSTANCE);
            cover.add(paragraph("", "Extra cover page"));
        }
        List<PageSequence> sequences = new ArrayList<>();
        sequences.add(PageSequence.body(PageLayout.a4Portrait(), cover));
        sequences.add(PageSequence.body(PageLayout.a4Portrait(), List.of(
                paragraph("contents", empty ? "Empty range" : "Contents anchor"),
                new TableOfContentsNode("Contents title", 1, 3, "by-visit"))));
        if (!empty) {
            sequences.add(PageSequence.body(PageLayout.a4Portrait(), List.of(paragraph("form-1", "First form body"))));
            sequences.add(PageSequence.body(PageLayout.a4Portrait(), List.of(paragraph("form-2", "Second form body"))));
        }
        return new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(), sequences,
                DocumentOptions.builder().navigation(DocumentNavigationOptions.builder()
                        .destinationMode(DocumentNavigationOptions.DestinationMode.PAGE_TOP)
                        .showBookmarks(true).initialTarget("cover").build()).build());
    }

    private BookmarkNode bookmark(String id, String title, String target, String parent) {
        return new BookmarkNode(id, title, target, parent, true);
    }

    private ParagraphNode paragraph(String id, String text) {
        return new ParagraphNode(id, List.of(new TextNode(text)));
    }

    private PDDocument render(DocumentModel document) throws Exception {
        return Loader.loadPDF(RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of())),
                document, RenderOptions.defaults()).content());
    }

    private List<PDOutlineItem> children(Iterable<PDOutlineItem> items) {
        List<PDOutlineItem> result = new ArrayList<>();
        items.forEach(result::add);
        return result;
    }

    private void assertTop(PDDocument pdf, PDPageXYZDestination destination) {
        assertThat(destination.getLeft()).isZero();
        assertThat(((org.apache.pdfbox.cos.COSNumber) destination.getCOSObject().get(3)).floatValue())
                .isEqualTo(destination.getPage().getMediaBox().getHeight());
        assertThat(destination.getCOSObject().get(4)).isSameAs(COSNull.NULL);
        assertThat(pdf.getPages().indexOf(destination.getPage())).isNotNegative();
    }

    private int targetPage(PDDocument pdf, String label) throws Exception {
        PDOutlineItem root = pdf.getDocumentCatalog().getDocumentOutline().getFirstChild().getNextSibling()
            .getNextSibling();
        for (PDOutlineItem visit : root.children()) {
            for (PDOutlineItem child : visit.children()) {
                if (child.getTitle().equals(label)) {
                    return pdf.getPages().indexOf(child.findDestinationPage(pdf)) + 1;
                }
            }
        }
        throw new AssertionError("Missing outline: " + label);
    }

    private String pageText(PDDocument pdf, int page) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(pdf);
    }

    private void assertContentsLinks(PDDocument pdf, int first, int second) throws Exception {
        for (int i = 0; i < pdf.getNumberOfPages(); i++) {
            String text = pageText(pdf, i + 1);
            if (text.contains("Occurrence One") && text.contains("Contents title")) {
                assertThat(text).contains(Integer.toString(first), Integer.toString(second));
                List<PDAnnotationLink> links = new ArrayList<>();
                for (org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation annotation : pdf.getPage(i)
                    .getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link) {
                        links.add(link);
                    }
                }
                links.sort(java.util.Comparator.comparingDouble(link -> -link.getRectangle().getUpperRightY()));
                List<Integer> targets = new ArrayList<>();
                for (PDAnnotationLink link : links) {
                    PDPageXYZDestination destination = (PDPageXYZDestination) link.getDestination();
                    targets.add(pdf.getPages().indexOf(destination.getPage()) + 1);
                }
                assertThat(targets).containsExactly(first, first, second, second);
                return;
            }
        }
        throw new AssertionError("Missing bookmark contents page");
    }
}
