package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.render.DocumentRenderer;
import io.github.leylaragg.letool.print.render.OutputCapability;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 使用受控 XHTML、OpenHTMLToPDF 和 PDFBox 生成 PDF 的线程安全渲染器。
 *
 * <p>实例只保存不可变字体配置，每次调用都会创建独立的排版器和临时工作区。</p>
 *
 * @author leyland
 */
public final class PdfDocumentRenderer implements DocumentRenderer {

    /** 当前 PDF 纵向链路明确支持的通用文档节点。 */
    private static final OutputCapability CAPABILITY = new OutputCapability(Set.of(
            SectionNode.class,
            HeadingNode.class,
            ParagraphNode.class,
            TableNode.class,
            PageBreakNode.class,
            TextNode.class,
            BookmarkNode.class,
            InternalLinkNode.class,
            AnnotationNode.class,
            TableOfContentsNode.class));

    /** 不可修改的宿主字体定义。 */
    private final List<PdfFont> fonts;

    /** 只产生固定 XHTML 的映射器。 */
    private final PdfXhtmlRenderer xhtmlRenderer;

    /** 排版完成后写入两类受控 PDF 批注。 */
    private final PdfAnnotationWriter annotationWriter;

    /** 宿主配置的可信临时根目录，供分段管线隔离请求文件。 */
    private final Path temporaryRoot;

    /**
     * 创建 PDF 渲染器。
     *
     * @param fonts 宿主拥有并授权使用的字体定义，可以为空
     */
    public PdfDocumentRenderer(List<PdfFont> fonts) {
        this(fonts, Path.of(System.getProperty("java.io.tmpdir"), "letool", "print-pdf"));
    }

    /**
     * 创建带显式临时根目录的 PDF 渲染器。
     *
     * @param fonts 宿主拥有并授权使用的字体定义，可以为空
     * @param temporaryRoot 可信且可写的 PDF 临时根目录
     */
    public PdfDocumentRenderer(List<PdfFont> fonts, Path temporaryRoot) {
        this.fonts = List.copyOf(Objects.requireNonNull(fonts, "fonts 不能为空"));
        this.xhtmlRenderer = new PdfXhtmlRenderer(this.fonts);
        this.annotationWriter = new PdfAnnotationWriter(this.fonts);
        this.temporaryRoot = Objects.requireNonNull(temporaryRoot, "临时根目录不能为空")
                .toAbsolutePath().normalize();
    }

    /** @return PDF 输出格式 */
    @Override
    public OutputFormat outputFormat() {
        return OutputFormat.PDF;
    }

    /** @return 当前 PDF 垂直链路支持的节点能力 */
    @Override
    public OutputCapability capability() {
        return CAPABILITY;
    }

    /**
     * 校验模型和能力后完成真实排版，并在写出前后应用治理限制。
     *
     * @param document 不可变通用文档模型
     * @param options 通用渲染限制
     * @param output 由打印引擎创建的受控输出
     * @return 已通过 PDF 结构检查的流式结果
     */
    @Override
    public PrintResult render(DocumentModel document, RenderOptions options, PrintOutput output) {
        Objects.requireNonNull(document, "document 不能为空");
        Objects.requireNonNull(options, "options 不能为空");
        Objects.requireNonNull(output, "output 不能为空");
        document.validate();
        CAPABILITY.requireSupports(document);

        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(
                temporaryRoot, options.maxTemporaryBytes())) {
            PdfRenderPlan plan = PdfRenderPlan.create(document);
            if (plan.tableOfContentsIndex() >= 0 || plan.bodyUnitCount() > 1) {
                return renderSegmented(document, options, plan, workspace, output);
            }

            return renderSingle(document, options, workspace, output);
        } catch (BaseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            if (causedByOutputLimit(exception)) {
                throw PrintRenderingException.outputLimitExceeded(
                        options.maxOutputBytes(),
                        exception);
            }
            throw PrintRenderingException.renderFailed(OutputFormat.PDF, exception);
        }
    }

    /** 物理分段排版、目录收敛和合并都在当前请求工作区内完成。 */
    private PrintResult renderSegmented(
            DocumentModel document,
            RenderOptions options,
            PdfRenderPlan plan,
            PdfRenderWorkspace workspace,
            PrintOutput output) throws IOException {
        PdfRenderIds fullIds = PdfRenderIds.create(document);
        PdfUnitRenderer unitRenderer = new PdfUnitRenderer(fonts);
        Map<Integer, PdfUnitResult> byIndex = new LinkedHashMap<>();
        for (int index = 0; index < plan.units().size(); index++) {
            PdfRenderUnit unit = plan.units().get(index);
            if (unit.kind() == PdfRenderUnit.Kind.BODY) {
                DocumentModel view = new DocumentModel(
                        document.metadata(), document.pageLayout(), unit.blocks());
                byIndex.put(index, unitRenderer.render(view, fullIds, options, workspace));
            }
        }
        PdfRenderIds tocIds = null;
        if (plan.tableOfContentsIndex() >= 0) {
            PdfTableOfContentsComposer composer = new PdfTableOfContentsComposer();
            List<PdfTocEntry> entries = composer.collect(document, fullIds);
            int assumedPages = 1;
            for (int pass = 0; pass < 5; pass++) {
                Map<HeadingNode, Integer> pages = segmentedPages(
                        plan, byIndex, entries, assumedPages);
                DocumentModel tocView = composer.composeContents(
                        document, plan.units().get(plan.tableOfContentsIndex()).tableOfContents(),
                        entries, pages);
                tocIds = PdfRenderIds.create(tocView);
                PdfUnitResult previous = byIndex.get(plan.tableOfContentsIndex());
                if (previous != null) {
                    workspace.discard(previous.file());
                }
                PdfUnitResult result = unitRenderer.render(tocView, tocIds, options, workspace);
                byIndex.put(plan.tableOfContentsIndex(), result);
                if (result.pageCount() == assumedPages) {
                    break;
                }
                assumedPages = result.pageCount();
                if (pass == 4) {
                    throw PrintRenderingException.renderFailed(
                            OutputFormat.PDF, new IllegalStateException("PDF 目录页码未收敛"));
                }
            }
        }
        List<PdfUnitResult> results = new ArrayList<>();
        for (int index = 0; index < plan.units().size(); index++) {
            results.add(byIndex.get(index));
        }
        return merge(document, options, results, fullIds, tocIds, workspace, output);
    }

    /** 把单段文档保存到受控文件，校验后再交给调用方。 */
    private PrintResult renderSingle(
            DocumentModel document,
            RenderOptions options,
            PdfRenderWorkspace workspace,
            PrintOutput output) throws IOException {
        List<AnnotationNode> annotations = annotationWriter.collect(document);
        String xhtml = xhtmlRenderer.render(document);
        Path finalFile = workspace.allocate();
        int pageCount;
        try (OutputStream temporary = workspace.openOutput(
                finalFile, options.maxOutputBytes())) {
            PdfRendererBuilder builder = createBuilder(xhtml, temporary);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.layout();
                pageCount = renderer.getRootBox().getLayer().getPages().size();
                if (pageCount > options.maxPages()) {
                    throw PrintRenderingException.pageLimitExceeded(options.maxPages());
                }
                // 页面坐标在排版后已经稳定，批注可直接写入最终 PDF 对象。
                try (PDDocument pdf = renderer.createPDFKeepOpen()) {
                    applyMetadata(pdf, document.metadata(), options);
                    annotationWriter.write(renderer, pdf, annotations);
                    pdf.save(temporary);
                }
            }
        }
        return validateAndTransfer(finalFile, pageCount, options, output);
    }

    /** 按目录假设页数计算各标题的最终一基页码。 */
    private Map<HeadingNode, Integer> segmentedPages(
            PdfRenderPlan plan, Map<Integer, PdfUnitResult> results,
            List<PdfTocEntry> entries, int tocPages) {
        Map<String, Integer> targetPages = new LinkedHashMap<>();
        int offset = 0;
        for (int index = 0; index < plan.units().size(); index++) {
            if (index == plan.tableOfContentsIndex()) {
                offset += tocPages;
                continue;
            }
            PdfUnitResult result = results.get(index);
            for (var target : result.snapshot().targets().entrySet()) {
                targetPages.put(target.getKey(), offset + target.getValue().pageIndex() + 1);
            }
            offset += result.pageCount();
        }
        Map<HeadingNode, Integer> pages = new IdentityHashMap<>();
        for (PdfTocEntry entry : entries) {
            Integer page = targetPages.get(entry.targetId());
            if (page == null) {
                throw PrintValidationException.invalidDocument("PDF 目录标题没有可见页面");
            }
            pages.put(entry.heading(), page);
        }
        return pages;
    }

    /** 合并单元文件后统一补写导航、批注和元数据。 */
    private PrintResult merge(
            DocumentModel document, RenderOptions options, List<PdfUnitResult> results,
            PdfRenderIds fullIds, PdfRenderIds tocIds, PdfRenderWorkspace workspace,
            PrintOutput output)
            throws IOException {
        Path finalFile = workspace.allocate();
        PDFMergerUtility merger = new PDFMergerUtility();
        int pageCount;
        try (PDDocument target = new PDDocument()) {
            pageCount = 0;
            for (PdfUnitResult result : results) {
                try (PDDocument source = Loader.loadPDF(result.file().toFile())) {
                    merger.appendDocument(target, source);
                }
                pageCount += result.pageCount();
            }
            if (pageCount > options.maxPages()) {
                throw PrintRenderingException.pageLimitExceeded(options.maxPages());
            }
            Map<String, String> sourceTargets = new LinkedHashMap<>();
            fullIds.linkIds().forEach((link, id) -> sourceTargets.put(id, link.targetId()));
            if (tocIds != null) {
                tocIds.linkIds().forEach((link, id) -> sourceTargets.put(id, link.targetId()));
            }
            Map<String, PdfNavigationWriter.GlobalPosition> targets =
                    new PdfNavigationWriter().write(target, document, results, sourceTargets);
            annotationWriter.writeMerged(target, annotationWriter.collect(document), targets);
            applyMetadata(target, document.metadata(), options);
            try (OutputStream temporary = workspace.openOutput(
                    finalFile, options.maxOutputBytes())) {
                target.save(temporary);
            }
        }
        for (PdfUnitResult result : results) {
            workspace.discard(result.file());
        }
        return validateAndTransfer(finalFile, pageCount, options, output);
    }

    /** 创建只接收内存 XHTML、宿主字体和受限输出流的排版器。 */
    private PdfRendererBuilder createBuilder(String xhtml, OutputStream output) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(xhtml, null)
                .toStream(output)
                .withProducer("letool")
                .useUriResolver((baseUri, uri) -> null)
                .useExternalResourceAccessControl(
                        (uri, type) -> false,
                        ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)
                .useExternalResourceAccessControl(
                        (uri, type) -> false,
                        ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        for (PdfFont font : fonts) {
            builder.useFont(font::openStream, font.familyName());
        }
        return builder;
    }

    /** 根据渲染选项写入安全的标准 PDF 元数据。 */
    private static void applyMetadata(
            PDDocument pdf,
            DocumentMetadata metadata,
            RenderOptions options) {
        PDDocumentInformation information = pdf.getDocumentInformation();
        information.setProducer("letool");
        if (!options.includeDocumentMetadata()) {
            information.setTitle(null);
            information.setAuthor(null);
            return;
        }
        information.setTitle(metadata.title());
        information.setAuthor(metadata.author());
        if (metadata.language() != null) {
            pdf.getDocumentCatalog().setLanguage(metadata.language());
        }
    }

    /** 重新打开最终文件检查结构，再把完整 PDF 写给调用方。 */
    private PrintResult validateAndTransfer(
            Path finalFile,
            int expectedPageCount,
            RenderOptions options,
            PrintOutput output) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(finalFile.toFile())) {
            int actualPageCount = pdf.getNumberOfPages();
            if (actualPageCount != expectedPageCount || actualPageCount > options.maxPages()) {
                throw PrintRenderingException.renderFailed(
                        OutputFormat.PDF, new IOException("PDF 最终页数校验失败"));
            }
        }
        try (InputStream input = Files.newInputStream(finalFile)) {
            input.transferTo(output);
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("pageCount", Integer.toString(expectedPageCount));
        metadata.put("contentLength", Long.toString(Files.size(finalFile)));
        return output.complete(OutputFormat.PDF, metadata);
    }

    /** 输出库可能包装 IOException，因此沿原因链识别专用容量信号。 */
    private boolean causedByOutputLimit(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PdfRenderWorkspace.CapacityExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
