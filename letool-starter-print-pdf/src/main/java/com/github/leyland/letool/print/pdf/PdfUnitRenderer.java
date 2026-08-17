package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.render.BoundedRenderOutput;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 用独立 OpenHTMLToPDF 实例排版并保存一个正文或目录视图。
 *
 * @author leyland
 */
final class PdfUnitRenderer {
    private final List<PdfFont> fonts;
    private final PdfXhtmlRenderer xhtmlRenderer;
    private final PdfLayoutSnapshotter snapshotter = new PdfLayoutSnapshotter();

    /** 为每个单元复用相同的宿主字体配置。 */
    PdfUnitRenderer(List<PdfFont> fonts) {
        this.fonts = List.copyOf(fonts);
        this.xhtmlRenderer = new PdfXhtmlRenderer(this.fonts);
    }

    /** 排版文档视图并在登记前完成页数检查。 */
    PdfUnitResult render(
            DocumentModel view,
            PdfRenderIds ids,
            RenderOptions options,
            PdfRenderWorkspace workspace) throws IOException {
        String xhtml = xhtmlRenderer.render(view, ids, true);
        Path file = workspace.allocate();
        PdfRendererBuilder builder = builder(xhtml, options.maxOutputBytes());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            int pageCount = renderer.getRootBox().getLayer().getPages().size();
            if (pageCount > options.maxPages()) {
                throw com.github.leyland.letool.print.exception.PrintRenderingException
                        .pageLimitExceeded(options.maxPages());
            }
            try (PDDocument pdf = renderer.createPDFKeepOpen()) {
                PdfLayoutSnapshot snapshot = snapshotter.snapshot(renderer, ids);
                pdf.save(file.toFile());
                workspace.register(file);
                return new PdfUnitResult(file, pageCount, snapshot);
            }
        }
    }

    /** 创建拒绝外部资源且只使用宿主字体的单元排版器。 */
    private PdfRendererBuilder builder(String xhtml, long maxOutputBytes) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(xhtml, null)
                .toStream(new BoundedRenderOutput(maxOutputBytes))
                .withProducer("letool")
                .useUriResolver((baseUri, uri) -> null)
                .useExternalResourceAccessControl((uri, type) -> false,
                        ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)
                .useExternalResourceAccessControl((uri, type) -> false,
                        ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        for (PdfFont font : fonts) {
            builder.useFont(font::openStream, font.familyName());
        }
        return builder;
    }
}
