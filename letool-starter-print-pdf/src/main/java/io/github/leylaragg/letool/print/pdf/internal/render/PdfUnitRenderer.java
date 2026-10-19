package io.github.leylaragg.letool.print.pdf.internal.render;

import io.github.leylaragg.letool.print.pdf.internal.annotation.PdfAnnotations;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import io.github.leylaragg.letool.print.pdf.internal.font.PdfFontSource;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.EnumSet;
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
                ids, options, workspace, document.document().options(), document.styleSheet(), blocks);
    }

    /**
     * 测量并修正单元布局，再保存基础 PDF 和按实际坐标绘制的标记。
     *
     * @param xhtml 当前分页轮次生成的单元 XHTML
     * @param pageLayout 页面尺寸、区域定位和序列页数约束
     * @param initialPageNumber 当前序列的初始逻辑页码
     * @param ids 本次渲染的逻辑目标与原生节点映射
     * @param options 本次渲染的容量和页数限制
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
            // 测量阶段只执行布局，以实际区域高度修正显式页眉、页脚和正文居中位置。
            String adjusted = xhtml;
            if (xhtml.contains("data-free-text-key=")) {
                try (PdfBoxRenderer measure = builder(xhtml, OutputStream.nullOutputStream(),
                        initialPageNumber, documentOptions).buildPdfRenderer()) {
                    measure.layout();
                    freeText = PdfAnnotations.prepare(measure, xhtml, styles, documentOptions, fontCatalog);
                    adjusted = freeText.xhtml();
                }
            }
            if (pageLayout.headerTopPoints() >= 0 || pageLayout.footerBaselinePoints() >= 0
                    || pageLayout.centeredBody()) {
                try (PdfBoxRenderer measure = builder(adjusted, OutputStream.nullOutputStream(),
                        initialPageNumber, documentOptions).buildPdfRenderer()) {
                    measure.layout();
                    adjusted = PdfLayoutProcessor.adjust(measure, adjusted, pageLayout);
                }
            }

            // 目录使用本轮实际文字行校正标题与条目基线，不能按字号估算。
            if (blocks.stream().anyMatch(ContentsTitleNode.class::isInstance)) {
                try (PdfBoxRenderer measure = builder(adjusted, OutputStream.nullOutputStream(),
                        initialPageNumber, documentOptions).buildPdfRenderer()) {
                    measure.layout();
                    adjusted = PdfNativeContents.adjust(measure, adjusted, blocks, ids);
                }
            }

            // 每次移页都会改变后续行的可用空间，逐轮使用新坐标，直到所有字段行稳定。
            // 分段行与已移页行都有标记，同一行不会无限重复校正。
            while (true) {
                try (PdfBoxRenderer measure = builder(adjusted, OutputStream.nullOutputStream(),
                        initialPageNumber, documentOptions).buildPdfRenderer()) {
                    measure.layout();
                    requirePageLimit(measure.getRootBox().getLayer().getPages().size(), pageLayout, options);
                    String corrected = PdfLayoutProcessor.fragment(measure, adjusted);
                    if (corrected.equals(adjusted)) {
                        break;
                    }
                    adjusted = corrected;
                }
            }

            // 最终布局同时产生导航快照和绘制坐标，所有标记与实际写出的页面保持一致。
            PdfRendererBuilder builder = builder(adjusted, output, initialPageNumber, documentOptions);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.layout();
                pageCount = renderer.getRootBox().getLayer().getPages().size();
                requirePageLimit(pageCount, pageLayout, options);
                snapshot = PdfLayoutProcessor.snapshot(renderer, ids.targetIds(), ids.linkIds().values());
                marks = PdfLayoutProcessor.collectDrawing(renderer);
                contentsRules = PdfNativeContents.collect(renderer, blocks, ids, styles);
                // 默认页眉页脚仍检查边距容纳性；显式位置采用前面的测量修正结果。
                if (pageLayout.headerTopPoints() < 0 && pageLayout.footerBaselinePoints() < 0) {
                    snapshot.requireRegionsFit(pageLayout);
                }
                renderer.createPDF();
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
                PdfLayoutProcessor.writeDrawing(pdf, marks, captions, styles, documentOptions, pageLayout, fontCatalog);
                PdfNativeContents.write(pdf, contentsRules);
                PdfAnnotations.writeFreeText(pdf, freeText);
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
     * @param output 输出流，由调用方负责关闭；测量阶段使用空输出流
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
            // 每次调用独立读取并规范化字体源，产生的输入流交由本轮渲染器消费和关闭。
            FSSupplier<InputStream> supplier = () -> {
                try {
                    return new ByteArrayInputStream(PdfFontSource.read(font));
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
