package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.AnnotationNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintRenderingException;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.render.DocumentRenderer;
import com.github.leyland.letool.print.render.OutputCapability;
import com.github.leyland.letool.print.render.RenderedDocument;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import java.io.IOException;
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
 * <p>实例只保存不可变字体配置，每次调用都会创建独立的排版器和输出缓冲区。</p>
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
     * @return 可由标准 PDF 工具重新打开的产物
     */
    @Override
    public RenderedDocument render(DocumentModel document, RenderOptions options) {
        Objects.requireNonNull(document, "document 不能为空");
        Objects.requireNonNull(options, "options 不能为空");
        document.validate();
        CAPABILITY.requireSupports(document);

        PdfRenderPlan plan = PdfRenderPlan.create(document);
        if (plan.tableOfContentsIndex() >= 0 || plan.bodyUnitCount() > 1) {
            return renderSegmented(document, options, plan);
        }

        List<AnnotationNode> annotations = annotationWriter.collect(document);
        String xhtml = xhtmlRenderer.render(document);
        PdfOutputBuffer output = new PdfOutputBuffer(options.maxOutputBytes());
        try {
            PdfRendererBuilder builder = createBuilder(xhtml, output);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.layout();
                int pageCount = renderer.getRootBox().getLayer().getPages().size();
                if (pageCount > options.maxPages()) {
                    throw PrintRenderingException.pageLimitExceeded(options.maxPages());
                }
                // 页面绘制完成后保持 PDF 打开，批注才能复用最终排版坐标。
                try (PDDocument pdf = renderer.createPDFKeepOpen()) {
                    applyMetadata(pdf, document.metadata(), options);
                    annotationWriter.write(renderer, pdf, annotations);
                    pdf.save(output);
                }
                return renderedDocument(output, pageCount);
            }
        } catch (PrintRenderingException exception) {
            throw exception;
        } catch (PrintValidationException exception) {
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

    /** 物理分段排版、目录收敛和 PDFBox 合并都限制在单次请求工作区内。 */
    private RenderedDocument renderSegmented(
            DocumentModel document, RenderOptions options, PdfRenderPlan plan) {
        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(
                temporaryRoot, options.maxOutputBytes())) {
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
            return merge(document, options, results, fullIds, tocIds, workspace);
        } catch (PrintRenderingException | PrintValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            if (causedByOutputLimit(exception)) {
                throw PrintRenderingException.outputLimitExceeded(
                        options.maxOutputBytes(), exception);
            }
            throw PrintRenderingException.renderFailed(OutputFormat.PDF, exception);
        }
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
    private RenderedDocument merge(
            DocumentModel document, RenderOptions options, List<PdfUnitResult> results,
            PdfRenderIds fullIds, PdfRenderIds tocIds, PdfRenderWorkspace workspace)
            throws IOException {
        Path finalFile = workspace.allocate();
        PDFMergerUtility merger = new PDFMergerUtility();
        try (PDDocument target = new PDDocument()) {
            int pageCount = 0;
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
            target.save(finalFile.toFile());
            for (PdfUnitResult result : results) {
                workspace.discard(result.file());
            }
            workspace.register(finalFile);
            byte[] content = Files.readAllBytes(finalFile);
            if (content.length > options.maxOutputBytes()) {
                throw PrintRenderingException.outputLimitExceeded(
                        options.maxOutputBytes(), new IOException("PDF 输出容量越界"));
            }
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("pageCount", Integer.toString(pageCount));
            metadata.put("contentLength", Integer.toString(content.length));
            return new RenderedDocument(OutputFormat.PDF, content, metadata);
        }
    }

    /** 创建只接收内存 XHTML、宿主字体和受限输出流的排版器。 */
    private PdfRendererBuilder createBuilder(String xhtml, PdfOutputBuffer output) {
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

    /** 将容量治理后的字节和不含业务正文的统计信息交给核心模型。 */
    private RenderedDocument renderedDocument(PdfOutputBuffer output, int pageCount)
            throws PdfOutputBuffer.OutputLimitExceededException {
        byte[] content = output.toByteArray();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("pageCount", Integer.toString(pageCount));
        metadata.put("contentLength", Integer.toString(content.length));
        return new RenderedDocument(OutputFormat.PDF, content, metadata);
    }

    /** 输出库可能包装 IOException，因此沿原因链识别专用容量信号。 */
    private boolean causedByOutputLimit(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PdfOutputBuffer.OutputLimitExceededException) {
                return true;
            }
            if (current instanceof PdfRenderWorkspace.CapacityExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
