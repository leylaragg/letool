package com.github.leyland.letool.print.pdf;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintRenderingException;
import com.github.leyland.letool.print.render.DocumentRenderer;
import com.github.leyland.letool.print.render.OutputCapability;
import com.github.leyland.letool.print.render.RenderedDocument;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PDFCreationListener;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 使用受控 XHTML 和 OpenHTMLToPDF 生成单文档 PDF 的线程安全渲染器。
 *
 * <p>实例只保存不可变字体配置，每次调用都会创建独立的排版器和输出缓冲区。</p>
 *
 * @author leyland
 */
public final class PdfDocumentRenderer implements DocumentRenderer {

    /** 4A 阶段明确支持的通用文档节点。 */
    private static final OutputCapability CAPABILITY = new OutputCapability(Set.of(
            SectionNode.class,
            HeadingNode.class,
            ParagraphNode.class,
            TableNode.class,
            PageBreakNode.class,
            TextNode.class,
            BookmarkNode.class,
            InternalLinkNode.class));

    /** 不可修改的宿主字体定义。 */
    private final List<PdfFont> fonts;

    /** 只产生固定 XHTML 的映射器。 */
    private final PdfXhtmlRenderer xhtmlRenderer;

    /**
     * 创建 PDF 渲染器。
     *
     * @param fonts 宿主拥有并授权使用的字体定义，可以为空
     */
    public PdfDocumentRenderer(List<PdfFont> fonts) {
        this.fonts = List.copyOf(Objects.requireNonNull(fonts, "fonts 不能为空"));
        this.xhtmlRenderer = new PdfXhtmlRenderer(this.fonts);
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
                renderer.setListener(new MetadataListener(document.metadata(), options));
                renderer.createPDF();
                return renderedDocument(output, pageCount);
            }
        } catch (PrintRenderingException exception) {
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
            current = current.getCause();
        }
        return false;
    }

    /** 在 OpenHTMLToPDF 完成内部元数据初始化后写入框架元数据。 */
    private static final class MetadataListener implements PDFCreationListener {

        /** 当前文档元数据快照。 */
        private final DocumentMetadata metadata;

        /** 当前渲染选项。 */
        private final RenderOptions options;

        /** 创建单次渲染使用的元数据监听器。 */
        private MetadataListener(DocumentMetadata metadata, RenderOptions options) {
            this.metadata = metadata;
            this.options = options;
        }

        /** PDF 打开前不需要额外处理。 */
        @Override
        public void preOpen(PdfBoxRenderer renderer) {
        }

        /** 页面绘制前不需要额外处理。 */
        @Override
        public void preWrite(PdfBoxRenderer renderer, int pageCount) {
        }

        /** 保存前覆盖库内默认元数据，确保渲染选项最终生效。 */
        @Override
        public void onClose(PdfBoxRenderer renderer) {
            applyMetadata(renderer.getPdfDocument(), metadata, options);
        }
    }
}
