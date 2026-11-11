package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageNumbering;
import io.github.leylaragg.letool.print.document.PageRegion;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.FreeTextNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.pdf.testsupport.RenderedPdf;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 PDF 批注额度、跨页副本及两类文本批注的合并计数。
 *
 * @author leyland
 */
class PdfAnnotationLimitTest {

    @Test
    void shouldAllowTraditionalAnnotationsAboveDefaultBudget() throws Exception {
        try (PDDocument pdf = render(traditionalAnnotations(1_001), 1_001)) {
            assertThat(textAnnotations(pdf)).hasSize(1_001).allMatch(PDAnnotationText.class::isInstance);
        }
    }

    @Test
    void shouldRejectTraditionalAnnotationsAboveConfiguredBudget() {
        assertBudgetRejected(traditionalAnnotations(3), 1, 3, "传统批注声明数");
    }

    @ParameterizedTest
    @EnumSource(AnnotationType.class)
    void shouldRejectTraditionalAnnotationWhenBudgetIsZero(AnnotationType type) {
        DocumentModel model = document(List.of(paragraph("summary"), annotation(type, "summary")));

        assertBudgetRejected(model, 0, 1, "传统批注声明数");
    }

    @Test
    void shouldRejectFlowFreeTextWhenBudgetIsZero() {
        assertBudgetRejected(document(List.of(freeText())), 0, 1, "当前排版单元物理数");
    }

    @Test
    void shouldRejectFlowFreeTextAboveConfiguredBudget() {
        assertBudgetRejected(document(List.of(freeText(), freeText(), freeText())), 1, 3, "当前排版单元物理数");
    }

    /** 首个单元已超限就停止，不能把三条误报为两个单元合计的六条。 */
    @Test
    void shouldReportUnitCountAsDocumentLowerBoundWhenRejectingEarly() {
        List<BlockNode> body = List.of(freeText(), freeText(), freeText());
        DocumentModel model = new DocumentModel(DocumentMetadata.empty(), styles(), List.of(
                PageSequence.body(PageLayout.a4Portrait(), body), PageSequence.body(PageLayout.a4Portrait(), body)));

        assertBudgetRejected(model, 2, 3, "当前排版单元物理数", "整份文档至少达到该数");
    }

    /** 单个排版单元已超过旧硬限制，写入器和最终合并都必须使用新额度。 */
    @Test
    void shouldAllowPhysicalFreeTextCopiesAboveDefaultBudget() throws Exception {
        DocumentModel model = repeatedHeaderDocument(26, 40, 1);

        try (PDDocument pdf = render(model, 1_040)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(40);
            assertThat(textAnnotations(pdf)).hasSize(1_040).allMatch(PDAnnotationFreeText.class::isInstance);
            for (PDPage page : pdf.getPages()) {
                assertThat(page.getAnnotations()).hasSize(26);
            }
        }
    }

    /** 两个单元各自未超限，最终仍要合计传统便签、传统文本框和流式文本框。 */
    @Test
    void shouldCountMixedAnnotationsAcrossMergedSequences() {
        DocumentModel model = new DocumentModel(DocumentMetadata.empty(), styles(), List.of(
                PageSequence.body(PageLayout.a4Portrait(), List.of(paragraph("first"), freeText())),
                PageSequence.body(PageLayout.a4Portrait(), List.of(paragraph("second"),
                        annotation(AnnotationType.TEXT_NOTE, "second"), annotation(AnnotationType.FREE_TEXT, "second")))));

        assertBudgetRejected(model, 2, 3, "合并文档既有物理批注与待追加传统声明合计");
    }

    /** 一个逻辑页眉在四页上形成四个独立物理批注，不能按节点去重。 */
    @Test
    void shouldCountRepeatedHeaderCopiesAcrossMergedSequences() {
        assertBudgetRejected(repeatedHeaderDocument(1, 2, 2), 3, 4, "最终合并整份文档物理批注总数");
    }

    @Test
    void shouldKeepAllPhysicalCopiesAtConfiguredBudget() throws Exception {
        try (PDDocument pdf = render(repeatedHeaderDocument(1, 2, 2), 4)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(4);
            assertThat(textAnnotations(pdf)).hasSize(4);
            for (PDPage page : pdf.getPages()) {
                assertThat(page.getAnnotations()).singleElement().isInstanceOf(PDAnnotationFreeText.class);
            }
        }
    }

    @Test
    void shouldExcludeNavigationLinksFromZeroBudget() throws Exception {
        try (PDDocument pdf = render(document(List.of(linkedParagraph())), 0)) {
            assertThat(textAnnotations(pdf)).isEmpty();
            assertThat(pdf.getPage(0).getAnnotations()).hasSize(2).allMatch(PDAnnotationLink.class::isInstance);
        }
    }

    @Test
    void shouldExcludeNavigationLinksWhenTextBudgetIsFull() throws Exception {
        DocumentModel model = document(List.of(linkedParagraph(), annotation(AnnotationType.TEXT_NOTE, "summary")));

        try (PDDocument pdf = render(model, 1)) {
            assertThat(textAnnotations(pdf)).singleElement().isInstanceOf(PDAnnotationText.class);
            assertThat(pdf.getPage(0).getAnnotations()).filteredOn(PDAnnotationLink.class::isInstance).hasSize(2);
        }
    }

    private void assertBudgetRejected(DocumentModel document, int maximum, int actual, String... countScopes) {
        RenderOptions options = options(maximum);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThatThrownBy(() -> renderer().render(document, options, new PrintOutput(output, options.maxOutputBytes())))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("实际数量 " + actual)
                .hasMessageContaining("配置额度 " + maximum)
                .satisfies(exception -> assertThat(exception.getMessage()).contains(countScopes));
        assertThat(output.size()).isZero();
    }

    private DocumentModel traditionalAnnotations(int count) {
        List<BlockNode> body = new ArrayList<>();
        body.add(paragraph("summary"));
        for (int index = 0; index < count; index++) {
            body.add(annotation(AnnotationType.TEXT_NOTE, "summary"));
        }
        return document(body);
    }

    private DocumentModel repeatedHeaderDocument(int annotationsPerPage, int pagesPerSequence, int sequenceCount) {
        List<BlockNode> header = new ArrayList<>();
        for (int index = 0; index < annotationsPerPage; index++) {
            header.add(freeText());
        }
        List<BlockNode> body = new ArrayList<>();
        for (int index = 0; index < pagesPerSequence; index++) {
            if (index > 0) {
                body.add(PageBreakNode.INSTANCE);
            }
            body.add(paragraph(""));
        }
        PageSequence sequence = new PageSequence(PageLayout.a4Portrait(), new PageRegion(header), PageRegion.empty(),
                PageNumbering.counted(), body);
        List<PageSequence> sequences = new ArrayList<>();
        for (int index = 0; index < sequenceCount; index++) {
            sequences.add(sequence);
        }
        return new DocumentModel(DocumentMetadata.empty(), styles(), sequences);
    }

    private DocumentModel document(List<BlockNode> blocks) {
        return new DocumentModel(DocumentMetadata.empty(), styles(), List.of(PageSequence.body(PageLayout.a4Portrait(), blocks)));
    }

    private ParagraphNode paragraph(String id) {
        return new ParagraphNode(id, List.of(new TextNode("页面正文")));
    }

    private ParagraphNode linkedParagraph() {
        return new ParagraphNode("summary", List.of(new TextNode("页面正文"),
                new InternalLinkNode("summary", List.of(new TextNode("链接一"))),
                new InternalLinkNode("summary", List.of(new TextNode("链接二")))));
    }

    private AnnotationNode annotation(AnnotationType type, String target) {
        int width = type == AnnotationType.TEXT_NOTE ? 6_000 : 50_000;
        int height = type == AnnotationType.TEXT_NOTE ? 6_000 : 20_000;
        return new AnnotationNode(type, target, AnnotationPlacement.TOP_LEFT, width, height, 0, 0, "审核人", "复核批注");
    }

    private FreeTextNode freeText() {
        return new FreeTextNode("", "tiny", "", "审核人", "批注");
    }

    private StyleSheet styles() {
        return StyleSheet.builder().text("tiny", TextStyle.builder().fontFamily("Droid Sans Fallback")
                .fontSize(DocumentLength.points(1)).lineHeight(1).build()).build();
    }

    private List<PDAnnotation> textAnnotations(PDDocument pdf) throws Exception {
        List<PDAnnotation> annotations = new ArrayList<>();
        for (PDPage page : pdf.getPages()) {
            for (PDAnnotation annotation : page.getAnnotations()) {
                if (annotation instanceof PDAnnotationText || annotation instanceof PDAnnotationFreeText) {
                    annotations.add(annotation);
                }
            }
        }
        return annotations;
    }

    private PDDocument render(DocumentModel document, int maximum) throws Exception {
        return Loader.loadPDF(RenderedPdf.render(renderer(), document, options(maximum)).content());
    }

    private RenderOptions options(int maximum) {
        return new RenderOptions(2_500, 100L * 1024 * 1024, 300L * 1024 * 1024, true, maximum);
    }

    private OpenHtmlPdfRenderer renderer() {
        PdfFont font = new PdfFont("Droid Sans Fallback", FontWeight.NORMAL, this::openFont, true);
        return new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(font)));
    }

    private InputStream openFont() {
        return Objects.requireNonNull(getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"));
    }
}
