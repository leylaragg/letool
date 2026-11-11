package io.github.leylaragg.letool.print.pdf.internal.render;

import io.github.leylaragg.letool.print.pdf.internal.annotation.PdfAnnotations;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import io.github.leylaragg.letool.print.pdf.internal.font.PdfFontResources;
import io.github.leylaragg.letool.print.pdf.internal.layout.PdfLayoutProcessor;
import io.github.leylaragg.letool.print.pdf.internal.layout.PdfLayoutSnapshot;
import io.github.leylaragg.letool.print.pdf.internal.navigation.PdfNativeContents;
import io.github.leylaragg.letool.print.pdf.internal.navigation.PdfRenderIds;
import io.github.leylaragg.letool.print.pdf.internal.resource.PdfRenderWorkspace;
import io.github.leylaragg.letool.print.pdf.internal.resource.PdfUnitResult;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentOptions;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.BodyCaptionNode;
import io.github.leylaragg.letool.print.document.node.ContentsTitleNode;
import io.github.leylaragg.letool.print.document.style.FontPosture;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

/**
 * 测量、校正并输出一个正文或目录页面序列。
 *
 * <p>每轮排版器独立管理布局和 PDF 文档，字体源及度量由当前请求共用。</p>
 *
 * @author leyland
 */
final class PdfUnitRenderer {
    /** 所有排版单元共用的不可变字体目录。 */
    private final PdfFontCatalog fontCatalog;
    private final PdfFontResources fonts;

    /** 把文档单元转换为受控 XHTML。 */
    private final PdfXhtmlRenderer xhtmlRenderer;

    /** 为每个单元复用相同的宿主字体配置。 */
    PdfUnitRenderer(PdfFontCatalog fontCatalog) {
        this(fontCatalog, new PdfFontResources(RenderOptions.DEFAULT_MAX_FONT_CACHE_BYTES));
    }

    /**
     * 创建复用请求字体资源的页面序列排版器。
     *
     * @param fontCatalog 宿主字体目录
     * @param fonts 本次渲染共用的字体资源
     */
    PdfUnitRenderer(PdfFontCatalog fontCatalog, PdfFontResources fonts) {
        this.fontCatalog = fontCatalog;
        this.fonts = fonts;
        this.xhtmlRenderer = new PdfXhtmlRenderer(fontCatalog);
    }

    /**
     * 按本轮页码和版式排版一个页面序列。
     *
     * @param document 已完成校验的文档计划
     * @param sequence 当前页面序列及其版式
     * @param blocks 本轮展开的正文或目录块
     * @param pagination 本轮的分页输入
     * @param ids 当前序列的目标与导航标记
     * @param options 本次请求的渲染额度
     * @param workspace 当前请求的临时工作区
     * @return 当前序列的 PDF 文件、实际页数和布局位置
     * @throws IOException 字体或单元 PDF 读写失败时抛出
     */
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
                ids, options, workspace, document.document().options(), document.styleSheet(), blocks);
    }

    /**
     * 测量并修正单元布局，使用稳定布局保存 PDF，再按实测坐标写入原生绘制和批注。
     *
     * <p>每轮独立释放 PDF 文档和排版器，覆盖只测量及中途失败的路径。</p>
     *
     * @param xhtml 当前分页轮次生成的单元 XHTML
     * @param pageLayout 页面尺寸、区域定位和序列页数约束
     * @param initialPageNumber 当前序列的初始逻辑页码
     * @param ids 本次渲染的逻辑目标与原生节点映射
     * @param options 本次渲染的页数、批注和产物额度
     * @param workspace 管理输出文件生命周期的临时工作区
     * @param documentOptions 文档级默认字体和缺字回退配置
     * @param styles 用于目录和正文图例的命名样式
     * @param blocks 当前单元展开后的块节点
     * @return 最终单元文件、物理页数与布局快照；文件由工作区管理
     * @throws IOException 字体、PDF 或工作区文件读写失败
     */
    private PdfUnitResult renderXhtml(
            String xhtml,
            PageLayout pageLayout,
            int initialPageNumber,
            PdfRenderIds ids,
            RenderOptions options,
            PdfRenderWorkspace workspace,
            DocumentOptions documentOptions,
            StyleSheet styles,
            List<BlockNode> blocks) throws IOException {
        Path file = workspace.allocate();
        PdfLayoutSnapshot snapshot;
        PdfLayoutProcessor.Drawing marks;
        List<PdfNativeContents.Rule> contentsRules;
        int pageCount;
        PdfAnnotations.Prepared freeText = PdfAnnotations.empty(xhtml);
        try (OutputStream output = workspace.openOutput(file, options.maxOutputBytes())) {
            String adjusted = xhtml;
            // 先测出文本框实际宽高，再据此修正分页用的占位。
            if (xhtml.contains("data-free-text-key=")) {
                try (PdfBoxRenderer measure = builder(xhtml, OutputStream.nullOutputStream(),
                        initialPageNumber, documentOptions).buildPdfRenderer();
                        PDDocument pdf = measure.getPdfDocument()) {
                    measure.layout();
                    freeText = PdfAnnotations.prepare(measure, xhtml, styles, documentOptions, fontCatalog, fonts);
                    adjusted = freeText.xhtml();
                }
            }
            // 显式页眉页脚与正文居中使用修正文本框后的实际区域高度。
            if (pageLayout.headerTopPoints() >= 0 || pageLayout.footerBaselinePoints() >= 0
                    || pageLayout.centeredBody()) {
                try (PdfBoxRenderer measure = builder(adjusted, OutputStream.nullOutputStream(),
                        initialPageNumber, documentOptions).buildPdfRenderer();
                        PDDocument pdf = measure.getPdfDocument()) {
                    measure.layout();
                    adjusted = PdfLayoutProcessor.adjust(measure, adjusted, pageLayout);
                }
            }

            // 目录使用本轮实际文字行校正标题与条目基线，不能按字号估算。
            if (blocks.stream().anyMatch(ContentsTitleNode.class::isInstance)) {
                try (PdfBoxRenderer measure = builder(adjusted, OutputStream.nullOutputStream(),
                        initialPageNumber, documentOptions).buildPdfRenderer();
                        PDDocument pdf = measure.getPdfDocument()) {
                    measure.layout();
                    adjusted = PdfNativeContents.adjust(measure, adjusted, blocks, ids);
                }
            }

            // 移页改变后续行的可用空间，需要用新坐标继续校正。分段或已移页的行带有处理标记。
            while (true) {
                try (PdfBoxRenderer renderer = builder(adjusted, output,
                        initialPageNumber, documentOptions).buildPdfRenderer();
                        PDDocument pdf = renderer.getPdfDocument()) {
                    renderer.layout();
                    pageCount = renderer.getRootBox().getLayer().getPages().size();
                    requirePageLimit(pageCount, pageLayout, options);
                    String corrected = PdfLayoutProcessor.fragment(renderer, adjusted);
                    if (!corrected.equals(adjusted)) {
                        adjusted = corrected;
                        continue;
                    }

                    // 只有内容无需再修正时才写出 PDF。坐标与产物来自同一次布局。
                    snapshot = PdfLayoutProcessor.snapshot(renderer, ids.targetIds(), ids.linkIds().values());
                    marks = PdfLayoutProcessor.collectDrawing(renderer);
                    contentsRules = PdfNativeContents.collect(renderer, blocks, ids, styles);
                    // 默认页眉页脚仍检查边距容纳性；显式位置采用前面的测量修正结果。
                    if (pageLayout.headerTopPoints() < 0 && pageLayout.footerBaselinePoints() < 0) {
                        snapshot.requireRegionsFit(pageLayout);
                    }
                    renderer.createPDF();
                    break;
                }
            }
        }

        // 基础输出流关闭后才重新打开 PDF，图例和线条写入另一个受容量限制的工作区文件。
        List<BodyCaptionNode> captions = DocumentTraversal.depthFirstBlocks(blocks).stream()
                .filter(BodyCaptionNode.class::isInstance)
                .map(BodyCaptionNode.class::cast).toList();
        if (marks.hasUnderlines() || !captions.isEmpty() || !contentsRules.isEmpty()
                || freeText.hasBoxes()) {
            Path painted = workspace.allocate();
            try (PDDocument pdf = Loader.loadPDF(file.toFile());
                    OutputStream target = workspace.openOutput(painted, options.maxOutputBytes())) {
                PdfLayoutProcessor.writeDrawing(pdf, marks, captions, styles, documentOptions, pageLayout, fontCatalog, fonts);
                PdfNativeContents.write(pdf, contentsRules);
                PdfAnnotations.writeFreeText(pdf, freeText, options, fonts);
                pdf.save(target);
            }
            // 追加绘制并保存成功后再释放基础文件，失败路径由工作区统一清理。
            workspace.discard(file);
            file = painted;
        }
        return new PdfUnitResult(file, pageCount, snapshot);
    }

    /** 每轮测量和最终输出使用同一页数上限，超限时停止继续排版。 */
    private static void requirePageLimit(int pageCount, PageLayout layout, RenderOptions options) {
        if (pageCount > options.maxPages() || layout.maxPages() > 0 && pageCount > layout.maxPages()) {
            throw PrintRenderingException.pageLimitExceeded(options.maxPages());
        }
    }

    /**
     * 创建拒绝外部资源且只使用宿主字体的单元排版器。
     *
     * @param xhtml 当前测量或输出阶段使用的 XHTML
     * @param output 输出流，由调用方负责关闭；只有稳定布局会调用 createPDF 写入
     * @param initialPageNumber 当前序列的初始逻辑页码
     * @param documentOptions 文档字体选项，用于配置明确声明的缺字回退族
     * @return 本阶段独立使用的排版器构建器
     */
    private PdfRendererBuilder builder(
            String xhtml, OutputStream output, int initialPageNumber,
            DocumentOptions documentOptions) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(xhtml, null)
                .toStream(output)
                .withProducer("letool")
                .useInitialPageNumber(initialPageNumber)
                .useCacheStore(PdfRendererBuilder.CacheStore.PDF_FONT_METRICS, fonts.metricsCache())
                .useUriResolver((baseUri, uri) -> null)
                .useExternalResourceAccessControl((uri, type) -> false,
                        ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)
                .useExternalResourceAccessControl((uri, type) -> false,
                        ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);

        // 原生几何节点和真实字体源参与每一轮测量，避免测量与最终绘制采用不同尺寸。
        builder.useObjectDrawerFactory(new PdfShapeDrawerFactory());
        for (PdfFont font : fontCatalog.fonts()) {
            int weight = font.weight() == FontWeight.BOLD
                    ? 700 : 400;
            // 只复用当前请求的规范化字节；每轮仍使用独立流和文档所属字体对象。
            FSSupplier<InputStream> supplier = () -> {
                try {
                    return fonts.openStream(font);
                } catch (IOException exception) {
                    throw new IllegalStateException("字体资源无法读取", exception);
                }
            };
            FontStyle posture = font.posture() == FontPosture.ITALIC ? FontStyle.ITALIC : FontStyle.NORMAL;

            // 明确声明的缺字回退族优先补字，其余字体保留普通字体面的注册顺序。
            if (font.familyName().equals(documentOptions.glyphFallbackFamily())) {
                builder.useFont(supplier, font.familyName(), weight, posture, true,
                        EnumSet.of(FSFontUseCase.FALLBACK_PRE));
            } else {
                builder.useFont(supplier, font.familyName(), weight, posture, true);
            }
        }
        return builder;
    }
}
