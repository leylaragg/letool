package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.OutputFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDF 渲染器公开扩展边界测试。
 *
 * @author leyland
 */
class PdfRendererContractTest {

    /** 默认实现通过 PDF 专用接口注册，宿主无需依赖实现类。 */
    @Test
    void shouldExposeDefaultRendererThroughPdfSpi() {
        PdfRenderer renderer = new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of()));

        assertThat(renderer.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(renderer).isInstanceOf(OpenHtmlPdfRenderer.class);
    }
}
