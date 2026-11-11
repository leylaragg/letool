package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.FreeTextNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.CellStyle;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TextStyle;
import io.github.leylaragg.letool.print.pdf.testsupport.RenderedPdf;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过正文、批注及 PDF 合并验证字体源复用、预算回退与请求隔离。
 *
 * @author leyland
 */
class PdfFontReuseTest {

    @TempDir
    Path temporary;

    @Test
    void readsFontSourceOnceAcrossSequencesAndMeasurementPasses() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        OpenHtmlPdfRenderer renderer = renderer(opens, closes);

        byte[] bytes = RenderedPdf.render(renderer, document(false), RenderOptions.defaults()).content();

        assertDocument(bytes);
        assertThat(opens.get()).isEqualTo(1);
        assertThat(closes.get()).isEqualTo(1);
    }

    @Test
    void reusesSameSourceForFinalFullFontEmbedding() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        OpenHtmlPdfRenderer renderer = renderer(opens, closes);

        byte[] bytes = RenderedPdf.render(renderer, document(true), RenderOptions.defaults()).content();

        assertDocument(bytes);
        assertThat(opens.get()).isEqualTo(1);
        assertThat(closes.get()).isEqualTo(1);
    }

    @Test
    void readsFreshSourceForNextRequestOnSameRenderer() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        OpenHtmlPdfRenderer renderer = renderer(opens, closes);
        DocumentModel model = document(false);

        assertDocument(RenderedPdf.render(renderer, model, RenderOptions.defaults()).content());
        assertDocument(RenderedPdf.render(renderer, model, RenderOptions.defaults()).content());

        assertThat(opens.get()).isEqualTo(2);
        assertThat(closes.get()).isEqualTo(2);
    }

    @Test
    void keepsFontAndPdfResourcesIsolatedForConcurrentRequests() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        OpenHtmlPdfRenderer renderer = renderer(opens, closes);
        DocumentModel model = document(false);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> RenderedPdf.render(renderer, model, RenderOptions.defaults()).content());
            var second = executor.submit(() -> RenderedPdf.render(renderer, model, RenderOptions.defaults()).content());
            assertDocument(first.get());
            assertDocument(second.get());
        } finally {
            executor.shutdownNow();
        }

        assertThat(opens.get()).isEqualTo(2);
        assertThat(closes.get()).isEqualTo(2);
    }

    @Test
    void rendersWithoutRetainingFontBytesWhenCacheIsDisabled() throws Exception {
        assertUncachedRendering(0);
    }

    @Test
    void rendersNormallyWhenFontDoesNotFitCacheBudget() throws Exception {
        assertUncachedRendering(1);
    }

    @Test
    void reusesSourceForTraditionalFreeTextAppearanceInMergedDocument() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        DocumentModel model = DocumentModel.singleSequence(DocumentMetadata.empty(), PageLayout.a4Portrait(), List.of(
                new ParagraphNode("summary", List.of(new TextNode("传统批注正文"))),
                new AnnotationNode(AnnotationType.FREE_TEXT, "summary", AnnotationPlacement.BOTTOM_LEFT,
                        50_000, 20_000, 0, 0, "", "传统文本框中文内容")));

        byte[] bytes = RenderedPdf.render(renderer(opens, closes), model, RenderOptions.defaults()).content();

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("传统批注正文");
            assertThat(pdf.getPage(0).getAnnotations()).singleElement()
                    .satisfies(annotation -> assertThat(annotation.getContents()).isEqualTo("传统文本框中文内容"));
        }
        assertThat(opens.get()).isEqualTo(1);
        assertThat(closes.get()).isEqualTo(1);
    }

    private void assertUncachedRendering(long budget) throws Exception {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        RenderOptions options = new RenderOptions(100, 10L * 1024 * 1024, 30L * 1024 * 1024, true, 1_000, budget);

        assertDocument(RenderedPdf.render(renderer(opens, closes), document(true), options).content());

        assertThat(opens.get()).isGreaterThan(1);
        assertThat(closes.get()).isEqualTo(opens.get());
    }

    private OpenHtmlPdfRenderer renderer(AtomicInteger opens, AtomicInteger closes) {
        PdfFont font = new PdfFont("Droid Sans Fallback", FontWeight.NORMAL, () -> {
            opens.incrementAndGet();
            InputStream source = Objects.requireNonNull(getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"));
            return new FilterInputStream(source) {
                @Override
                public void close() throws IOException {
                    closes.incrementAndGet();
                    super.close();
                }
            };
        }, true);
        return new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(font)), temporary, null);
    }

    private DocumentModel document(boolean fullFonts) {
        StyleSheet styles = StyleSheet.builder()
                .text("body", TextStyle.builder().fontFamily("Droid Sans Fallback")
                        .fontSize(DocumentLength.points(10)).build())
                .paragraph("body", ParagraphStyle.builder().textStyleName("body").build())
                .cell("note", CellStyle.builder().build())
                .build();
        List<PageSequence> sequences = new ArrayList<>();
        for (String chapter : List.of("甲", "乙", "丙")) {
            sequences.add(PageSequence.body(PageLayout.a4Portrait(), List.of(
                    new ParagraphNode("", "body", List.of(new TextNode("受试者资料" + chapter + "中文正文"))),
                    new FreeTextNode("", "body", "note", "", "采集变量说明" + chapter))));
        }
        return new DocumentModel(DocumentMetadata.empty(), styles, sequences,
                DocumentOptions.builder().defaultFontFamily("Droid Sans Fallback")
                        .glyphFallbackFamily("Droid Sans Fallback").fullFontEmbedding(fullFonts).build());
    }

    private void assertDocument(byte[] bytes) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+", ""))
                    .contains("受试者资料甲中文正文", "受试者资料乙中文正文", "受试者资料丙中文正文");
            List<String> notes = new ArrayList<>();
            for (var page : pdf.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    notes.add(annotation.getContents());
                }
            }
            assertThat(notes).containsExactly("采集变量说明甲", "采集变量说明乙", "采集变量说明丙");
        }
    }
}
