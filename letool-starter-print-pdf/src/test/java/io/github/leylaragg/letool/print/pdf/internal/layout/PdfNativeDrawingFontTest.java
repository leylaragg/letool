package io.github.leylaragg.letool.print.pdf.internal.layout;

import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BodyCaptionNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import io.github.leylaragg.letool.print.pdf.internal.font.PdfFontResources;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证图例为当前文档加载字体时复用同次渲染已读取的源字节。
 *
 * @author leyland
 */
class PdfNativeDrawingFontTest {

    @Test
    void shouldReuseRetainedSourceForNativeCaptionDrawing() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        PdfFont source = new PdfFont("Droid Sans Fallback", FontWeight.NORMAL, () -> {
            opens.incrementAndGet();
            return Objects.requireNonNull(getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"));
        }, true);
        PdfFontResources resources = new PdfFontResources(64L * 1024 * 1024);
        resources.read(source);
        byte[] bytes;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            PdfNativeDrawing.write(pdf, new PdfNativeMarks.Marks(List.of(), Map.of(0, 200F)),
                    List.of(new BodyCaptionNode("", List.of(new TextNode("正文图例中文")), 10)),
                    StyleSheet.empty(), DocumentOptions.defaults(), PageLayout.a4Portrait(),
                    PdfFontCatalog.of(List.of(source)), resources);
            pdf.save(output);
            bytes = output.toByteArray();
        }
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("正文图例中文");
        }
        assertThat(opens.get()).isEqualTo(1);
    }
}
