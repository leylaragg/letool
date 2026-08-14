package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.render.RenderedDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同一 PDF 渲染器并发处理不可变文档时不共享调用状态。
 *
 * @author leyland
 */
class PdfRendererConcurrencyTest {

    /** 多个调用可以并行读取同一渲染器、字体配置和文档快照。 */
    @Test
    void shouldRenderSameDocumentConcurrently() throws Exception {
        PdfDocumentRenderer renderer = renderer();
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(new ParagraphNode("", List.of(new TextNode("并发正文 concurrent PDF")))));
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            List<Callable<RenderedDocument>> tasks = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                tasks.add(() -> renderer.render(document, RenderOptions.defaults()));
            }
            List<Future<RenderedDocument>> futures = executor.invokeAll(tasks);
            for (Future<RenderedDocument> future : futures) {
                try (PDDocument pdf = Loader.loadPDF(future.get().content())) {
                    assertThat(pdf.getNumberOfPages()).isEqualTo(1);
                    assertThat(new PDFTextStripper().getText(pdf))
                            .contains("并发正文 concurrent PDF");
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /** 创建每次调用都能打开独立字体流的渲染器。 */
    private PdfDocumentRenderer renderer() {
        PdfFont font = new PdfFont("Droid Sans Fallback", this::openTestFont, true);
        return new PdfDocumentRenderer(List.of(font));
    }

    /** 每次调用重新打开测试字体。 */
    private InputStream openTestFont() {
        return Objects.requireNonNull(
                getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"),
                "测试字体不存在");
    }
}
