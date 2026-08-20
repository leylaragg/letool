package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
        Path temporaryRoot = Files.createDirectories(Path.of(
                "target", "concurrency-workspace", UUID.randomUUID().toString())
                .toAbsolutePath().normalize());
        PdfDocumentRenderer renderer = renderer(temporaryRoot);
        DocumentModel document = DocumentModel.singleSequence(
                DocumentMetadata.empty(),
                PageLayout.a4Portrait(),
                List.of(
                        new TableOfContentsNode("目录", 1, 2),
                        new HeadingNode("first", 1, List.of(new TextNode("第一章"))),
                        new ParagraphNode("", List.of(new InternalLinkNode(
                                "summary", List.of(new TextNode("转到第二章"))))),
                        PageBreakNode.INSTANCE,
                        new HeadingNode("summary", 1, List.of(new TextNode("并发正文 concurrent PDF"))),
                        new AnnotationNode(
                                AnnotationType.TEXT_NOTE,
                                "summary",
                                AnnotationPlacement.TOP_RIGHT,
                                6_000,
                                6_000,
                                0,
                                0,
                                "审核人",
                                "并发批注")));
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            List<Callable<RenderedPdf>> tasks = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                tasks.add(() -> RenderedPdf.render(renderer, document, RenderOptions.defaults()));
            }
            List<Future<RenderedPdf>> futures = executor.invokeAll(tasks);
            for (Future<RenderedPdf> future : futures) {
                try (PDDocument pdf = Loader.loadPDF(future.get().content())) {
                    assertThat(pdf.getNumberOfPages()).isEqualTo(3);
                    assertThat(new PDFTextStripper().getText(pdf))
                            .contains("目录", "第一章", "并发正文 concurrent PDF");
                    assertThat(pdf.getPage(2).getAnnotations())
                            .singleElement()
                            .satisfies(annotation -> assertThat(annotation.getContents())
                                    .isEqualTo("并发批注"));
                }
            }
        } finally {
            executor.shutdownNow();
        }
        try (var files = Files.list(temporaryRoot)) {
            assertThat(files).isEmpty();
        }
        Files.delete(temporaryRoot);
    }

    /** 创建每次调用都能打开独立字体流的渲染器。 */
    private PdfDocumentRenderer renderer(Path temporaryRoot) {
        PdfFont font = new PdfFont("Droid Sans Fallback", this::openTestFont, true);
        return new PdfDocumentRenderer(List.of(font), temporaryRoot);
    }

    /** 每次调用重新打开测试字体。 */
    private InputStream openTestFont() {
        return Objects.requireNonNull(
                getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"),
                "测试字体不存在");
    }
}
