package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.DocumentTraversal;
import com.github.leyland.letool.print.document.Margins;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.PageOrientation;
import com.github.leyland.letool.print.document.node.AnnotationNode;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.ImageNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableOfContentsNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintRenderingException;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.render.BoundedRenderOutput;
import com.github.leyland.letool.print.render.DocumentRenderer;
import com.github.leyland.letool.print.render.OutputCapability;
import com.github.leyland.letool.print.render.RenderedDocument;
import org.docx4j.docProps.core.CoreProperties;
import org.docx4j.docProps.core.dc.elements.SimpleLiteral;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.STPageOrientation;
import org.docx4j.wml.SectPr;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 使用 docx4j 将通用文档模型写成可编辑 DOCX。
 *
 * <p>实例只保存不可变选项，每次渲染都会创建独立的 OOXML 包和请求上下文。</p>
 *
 * @author leyland
 */
public final class DocxDocumentRenderer implements DocumentRenderer {

    /** 当前基础链路已经能够直接表达的节点。 */
    private static final OutputCapability CAPABILITY = new OutputCapability(Set.of(
            SectionNode.class,
            HeadingNode.class,
            ParagraphNode.class,
            TableNode.class,
            PageBreakNode.class,
            TextNode.class,
            BookmarkNode.class,
            InternalLinkNode.class,
            TableOfContentsNode.class,
            ImageNode.class,
            AnnotationNode.class));

    /** 宿主级不可变 DOCX 配置。 */
    private final DocxRendererOptions rendererOptions;

    /**
     * 创建 DOCX 渲染器。
     *
     * @param rendererOptions 宿主级 DOCX 配置
     */
    public DocxDocumentRenderer(DocxRendererOptions rendererOptions) {
        this.rendererOptions = Objects.requireNonNull(rendererOptions, "rendererOptions 不能为空");
    }

    /** @return DOCX 输出格式 */
    @Override
    public OutputFormat outputFormat() {
        return OutputFormat.DOCX;
    }

    /** @return 当前 DOCX 链路支持的节点能力 */
    @Override
    public OutputCapability capability() {
        return CAPABILITY;
    }

    /**
     * 校验文档后创建独立 OOXML 包，并用受限输出流保存。
     *
     * @param document 不可变通用文档模型
     * @param options 通用渲染限制
     * @return 可由标准 Word 工具重新打开的 DOCX 产物
     */
    @Override
    public RenderedDocument render(DocumentModel document, RenderOptions options) {
        Objects.requireNonNull(document, "document 不能为空");
        Objects.requireNonNull(options, "options 不能为空");
        document.validate();
        CAPABILITY.requireSupports(document);
        requireNoDegradationInStrictMode(document);
        try {
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
            DocxRenderContext context = new DocxRenderContext(
                    wordPackage, rendererOptions, DocxRenderIds.create(document), document);
            new DocxStyleWriter().write(context);
            applyPageLayout(context, document.pageLayout());
            applyMetadata(context, document.metadata(), options.includeDocumentMetadata());
            new DocxBodyWriter().write(context, document.blocks());
            new DocxCompatibilityWriter().writeAnnotations(context);
            new DocxPackageValidator().validate(wordPackage);

            BoundedRenderOutput output = new BoundedRenderOutput(options.maxOutputBytes());
            wordPackage.save(output);
            byte[] content = output.toByteArray();
            return new RenderedDocument(
                    OutputFormat.DOCX, content, metadata(context, content.length));
        } catch (PrintValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            if (causedByOutputLimit(exception)) {
                throw PrintRenderingException.outputLimitExceeded(options.maxOutputBytes(), exception);
            }
            throw PrintRenderingException.renderFailed(OutputFormat.DOCX, exception);
        }
    }

    /** 将微米页面布局换算为 Word 的 twip 页面定义。 */
    private void applyPageLayout(DocxRenderContext context, PageLayout layout) {
        int width = layout.pageSize().widthMicrometers();
        int height = layout.pageSize().heightMicrometers();
        if (layout.orientation() == PageOrientation.LANDSCAPE) {
            int originalWidth = width;
            width = height;
            height = originalWidth;
        }
        SectPr section = context.factory().createSectPr();
        SectPr.PgSz pageSize = context.factory().createSectPrPgSz();
        pageSize.setW(BigInteger.valueOf(DocxUnits.micrometersToTwips(width)));
        pageSize.setH(BigInteger.valueOf(DocxUnits.micrometersToTwips(height)));
        if (layout.orientation() == PageOrientation.LANDSCAPE) {
            pageSize.setOrient(STPageOrientation.LANDSCAPE);
        }
        section.setPgSz(pageSize);
        section.setPgMar(pageMargins(context, layout.margins()));
        context.wordPackage().getMainDocumentPart().getJaxbElement().getBody().setSectPr(section);
    }

    /** 将四边边距写入页面定义。 */
    private SectPr.PgMar pageMargins(DocxRenderContext context, Margins margins) {
        SectPr.PgMar result = context.factory().createSectPrPgMar();
        result.setTop(BigInteger.valueOf(DocxUnits.micrometersToTwips(margins.topMicrometers())));
        result.setRight(BigInteger.valueOf(DocxUnits.micrometersToTwips(margins.rightMicrometers())));
        result.setBottom(BigInteger.valueOf(DocxUnits.micrometersToTwips(margins.bottomMicrometers())));
        result.setLeft(BigInteger.valueOf(DocxUnits.micrometersToTwips(margins.leftMicrometers())));
        result.setHeader(BigInteger.ZERO);
        result.setFooter(BigInteger.ZERO);
        result.setGutter(BigInteger.ZERO);
        return result;
    }

    /** 按通用选项决定是否写入标题、作者和语言。 */
    private void applyMetadata(
            DocxRenderContext context, DocumentMetadata metadata, boolean includeMetadata) {
        CoreProperties properties = context.wordPackage().getDocPropsCorePart().getJaxbElement();
        if (!includeMetadata) {
            properties.setTitle(null);
            properties.setCreator(null);
            properties.setLanguage(null);
            return;
        }
        var factory = new org.docx4j.docProps.core.dc.elements.ObjectFactory();
        if (metadata.title() != null) {
            properties.setTitle(factory.createTitle(literal(metadata.title())));
        }
        if (metadata.author() != null) {
            properties.setCreator(literal(metadata.author()));
        }
        if (metadata.language() != null) {
            properties.setLanguage(literal(metadata.language()));
        }
    }

    /** 创建只承载一个受控元数据值的 Dublin Core 文本。 */
    private SimpleLiteral literal(String value) {
        SimpleLiteral literal = new SimpleLiteral();
        literal.getContent().add(value);
        return literal;
    }

    /** 返回不含正文内容的稳定渲染统计。 */
    private Map<String, String> metadata(DocxRenderContext context, int contentLength) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("compatibilityMode", rendererOptions.compatibilityMode().name());
        metadata.put("degradedNodeCount", Long.toString(context.degradedNodeCount()));
        metadata.put("degradedNodeTypes", String.join(",", context.degradedNodeTypes()));
        metadata.put("fieldUpdateRequired", Boolean.toString(context.fieldUpdateRequired()));
        metadata.put("contentLength", Integer.toString(contentLength));
        return metadata;
    }

    /** 严格模式在分配 docx4j 包之前拒绝需要替代表达的节点。 */
    private void requireNoDegradationInStrictMode(DocumentModel document) {
        if (rendererOptions.compatibilityMode() != DocxCompatibilityMode.STRICT) {
            return;
        }
        for (var node : DocumentTraversal.depthFirst(document)) {
            if (node instanceof ImageNode || node instanceof AnnotationNode) {
                throw PrintValidationException.invalidDocument(
                        "DOCX 严格模式不支持需要兼容降级的节点");
            }
        }
    }

    /** docx4j 会包装输出异常，因此沿原因链识别容量信号。 */
    private boolean causedByOutputLimit(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof BoundedRenderOutput.OutputLimitExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
