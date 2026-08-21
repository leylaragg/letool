package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 用独立 OpenHTMLToPDF 实例排版并保存一个正文或目录视图。
 *
 * @author leyland
 */
final class PdfUnitRenderer {
    /** 所有排版单元共用的不可变字体目录。 */
    private final PdfFontCatalog fontCatalog;

    /** 把文档单元转换为受控 XHTML。 */
    private final PdfXhtmlRenderer xhtmlRenderer;

    /** 从 OpenHTMLToPDF 布局结果提取稳定坐标。 */
    private final PdfLayoutSnapshotter snapshotter = new PdfLayoutSnapshotter();

    /** 为每个单元复用相同的宿主字体配置。 */
    PdfUnitRenderer(PdfFontCatalog fontCatalog) {
        this.fontCatalog = fontCatalog;
        this.xhtmlRenderer = new PdfXhtmlRenderer(fontCatalog);
    }

    /** 按当前分页轮次排版一个页面序列正文单元。 */
    PdfUnitResult render(
            PdfDocumentPlan document,
            PdfSequencePlan sequence,
            List<BlockNode> blocks,
            PdfPaginationPlan pagination,
            PdfRenderIds ids,
            RenderOptions options,
            PdfRenderWorkspace workspace) throws IOException {
        String xhtml = xhtmlRenderer.render(
                document, sequence, blocks, pagination, ids, true);
        int initialPageNumber = pagination.sequence(
                sequence.sourceIndex()).initialPageNumber();
        return renderXhtml(xhtml, sequence.pageLayout(), initialPageNumber,
                ids, options, workspace);
    }

    /** 使用已经生成的 XHTML 完成一次受控布局和文件保存。 */
    private PdfUnitResult renderXhtml(
            String xhtml,
            PageLayout pageLayout,
            int initialPageNumber,
            PdfRenderIds ids,
            RenderOptions options,
            PdfRenderWorkspace workspace) throws IOException {
        Path file = workspace.allocate();
        try (OutputStream output = workspace.openOutput(file, options.maxOutputBytes())) {
            PdfRendererBuilder builder = builder(xhtml, output, initialPageNumber);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.layout();
                int pageCount = renderer.getRootBox().getLayer().getPages().size();
                if (pageCount > options.maxPages()) {
                    throw io.github.leylaragg.letool.print.exception.PrintRenderingException
                            .pageLimitExceeded(options.maxPages());
                }
                PdfLayoutSnapshot snapshot = snapshotter.snapshot(renderer, ids);
                snapshot.requireRegionsFit(pageLayout);
                renderer.createPDF();
                return new PdfUnitResult(file, pageCount, snapshot);
            }
        }
    }

    /** 创建拒绝外部资源且只使用宿主字体的单元排版器。 */
    private PdfRendererBuilder builder(
            String xhtml, OutputStream output, int initialPageNumber) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(xhtml, null)
                .toStream(output)
                .withProducer("letool")
                .useInitialPageNumber(initialPageNumber)
                .useUriResolver((baseUri, uri) -> null)
                .useExternalResourceAccessControl((uri, type) -> false,
                        ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)
                .useExternalResourceAccessControl((uri, type) -> false,
                        ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        for (PdfFont font : fontCatalog.fonts()) {
            int weight = font.weight() == io.github.leylaragg.letool.print.document.style.FontWeight.BOLD
                    ? 700 : 400;
            builder.useFont(font::openStream, font.familyName(), weight, FontStyle.NORMAL, true);
        }
        return builder;
    }
}
