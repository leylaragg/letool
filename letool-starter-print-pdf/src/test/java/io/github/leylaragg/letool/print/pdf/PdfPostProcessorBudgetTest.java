package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** 末尾外部文件只使用真实剩余额度，小文档不要求固定三倍临时空间。 */
class PdfPostProcessorBudgetTest {
    @TempDir
    Path directory;

    @Test
    void shouldPassRemainingTargetBudgetAndAccountProcessedFile() throws Exception {
        long maximum = 1024L * 1024;
        AtomicLong allowance = new AtomicLong();
        PdfFont font = new PdfFont("Droid Sans Fallback", FontWeight.NORMAL,
            () -> getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"), true);
        PdfPostProcessor processor = (source, target, boundedTarget, maxTargetBytes) -> {
            allowance.set(maxTargetBytes);
            Files.copy(source, boundedTarget);
        };
        OpenHtmlPdfRenderer renderer = new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(font)), directory, processor);
        DocumentModel document = new DocumentModel(DocumentMetadata.empty(), StyleSheet.empty(),
            List.of(PageSequence.body(PageLayout.a4Portrait(),
            List.of(new ParagraphNode("", List.of(new TextNode("Small native document")))))),
            DocumentOptions.builder().webOptimized(true).build());
        RenderedPdf result = RenderedPdf.render(renderer, document, new RenderOptions(5, maximum, maximum * 2, false));
        assertThat(result.content()).startsWith('%', 'P', 'D', 'F');
        assertThat(allowance.get()).isPositive().isLessThan(maximum);
    }
    @Test
    void shouldRejectOversizeBeforeWritingBeyondRemainingCapacity() throws Exception {
        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(directory, 12)) {
            Path source = workspace.allocate();
            try (java.io.OutputStream output = workspace.openOutput(source, 4)) {
                output.write(new byte[4]);
            }
            Path target = workspace.allocate();
            assertThat(workspace.remainingBytes()).isEqualTo(8);
            try (java.io.OutputStream output = workspace.openOutput(target, workspace.remainingBytes())) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> output.write(new byte[9]))
                    .isInstanceOf(PdfRenderWorkspace.CapacityExceededException.class);
            }
            assertThat(Files.size(target)).isZero();
            assertThat(workspace.activeBytes()).isEqualTo(4);
        }
    }
}
