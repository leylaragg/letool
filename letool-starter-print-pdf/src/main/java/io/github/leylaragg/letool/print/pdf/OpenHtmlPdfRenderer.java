package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.LineBreakNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.PageCountNode;
import io.github.leylaragg.letool.print.document.node.PageNumberNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import io.github.leylaragg.letool.print.render.DocumentFeature;
import io.github.leylaragg.letool.print.render.OutputCapability;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 使用受控 XHTML、OpenHTMLToPDF 和 PDFBox 生成 PDF 的线程安全渲染器。
 *
 * <p>实例只保存不可变字体和路径配置，每次调用都会建立独立的分页状态与临时工作区。</p>
 *
 * @author leyland
 */
public final class OpenHtmlPdfRenderer implements PdfRenderer {

    /** 当前纵向链路已经实现的通用节点和页面语义。 */
    private static final OutputCapability CAPABILITY = new OutputCapability(Set.of(
            SectionNode.class,
            HeadingNode.class,
            ParagraphNode.class,
            TableNode.class,
            PageBreakNode.class,
            TextNode.class,
            LineBreakNode.class,
            PageNumberNode.class,
            PageCountNode.class,
            BookmarkNode.class,
            InternalLinkNode.class,
            AnnotationNode.class,
            TableOfContentsNode.class), EnumSet.allOf(DocumentFeature.class));

    /** 已完成唯一性校验的宿主字体目录。 */
    private final PdfFontCatalog fontCatalog;

    /** 宿主配置的可信临时根目录。 */
    private final Path temporaryRoot;

    /**
     * 创建 PDF 渲染器。
     *
     * @param fontCatalog 宿主拥有并授权使用的字体目录
     */
    public OpenHtmlPdfRenderer(PdfFontCatalog fontCatalog) {
        this(fontCatalog, Path.of(System.getProperty("java.io.tmpdir"), "letool", "print-pdf"));
    }

    /**
     * 创建带显式临时根目录的 PDF 渲染器。
     *
     * @param fontCatalog 宿主拥有并授权使用的字体目录
     * @param temporaryRoot 可信且可写的 PDF 临时根目录
     */
    public OpenHtmlPdfRenderer(PdfFontCatalog fontCatalog, Path temporaryRoot) {
        this.fontCatalog = Objects.requireNonNull(fontCatalog, "fontCatalog 不能为空");
        this.temporaryRoot = Objects.requireNonNull(temporaryRoot, "临时根目录不能为空")
                .toAbsolutePath().normalize();
    }

    /** @return PDF 输出格式 */
    @Override
    public OutputFormat outputFormat() {
        return OutputFormat.PDF;
    }

    /** @return 当前 PDF 纵向链路支持的公共能力 */
    @Override
    public OutputCapability capability() {
        return CAPABILITY;
    }

    /**
     * 校验能力、收敛分页并统一组装最终 PDF。
     *
     * @param document 不可变通用文档模型
     * @param options 通用渲染限制
     * @param output 由打印引擎创建的受控输出
     * @return 已通过结构检查的流式结果
     */
    @Override
    public PrintResult render(DocumentModel document, RenderOptions options, PrintOutput output) {
        Objects.requireNonNull(document, "document 不能为空");
        Objects.requireNonNull(options, "options 不能为空");
        Objects.requireNonNull(output, "output 不能为空");
        CAPABILITY.requireSupports(document);

        try (PdfRenderWorkspace workspace = PdfRenderWorkspace.open(
                temporaryRoot, options.maxTemporaryBytes())) {
            PdfDocumentPlan plan = PdfDocumentPlan.create(document);
            // 样式错误在打开排版器前暴露，避免部分序列已经生成后才失败。
            PdfStyleCatalog.compile(plan.styleSheet(), fontCatalog);
            return renderPlanned(plan, options, workspace, output);
        } catch (BaseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            if (causedByOutputLimit(exception)) {
                throw PrintRenderingException.outputLimitExceeded(
                        options.maxOutputBytes(), exception);
            }
            throw PrintRenderingException.renderFailed(OutputFormat.PDF, exception);
        }
    }

    /** 分页输入稳定后才允许文档级语义进入最终 PDF。 */
    private PrintResult renderPlanned(
            PdfDocumentPlan document,
            RenderOptions options,
            PdfRenderWorkspace workspace,
            PrintOutput output) throws IOException {
        PdfTableOfContentsComposer contentsComposer = new PdfTableOfContentsComposer();
        List<PdfTocEntry> entries = hasTableOfContents(document)
                ? contentsComposer.collect(document.document(), document.renderIds()) : List.of();
        PdfPaginationPlanner paginationPlanner = new PdfPaginationPlanner(5);
        PdfPaginationPlan pagination = paginationPlanner.initial(document);
        List<PdfUnitResult> previousResults = List.of();

        while (true) {
            discard(previousResults, workspace);
            RenderPass pass = renderPass(
                    document, pagination, entries, contentsComposer, options, workspace);
            PdfPaginationPlan measured = paginationPlanner.advance(
                    document, pagination, pass.physicalPages(), pass.targetPages());
            if (measured.stable()) {
                return new PdfDocumentAssembler(fontCatalog).assemble(
                        document, pass.results(), pass.sourceTargets(),
                        options, workspace, output);
            }
            previousResults = pass.results();
            pagination = measured;
        }
    }

    /** 一轮中每个页面序列只打开一个排版器，页面版式不会相互污染。 */
    private RenderPass renderPass(
            PdfDocumentPlan document,
            PdfPaginationPlan pagination,
            List<PdfTocEntry> entries,
            PdfTableOfContentsComposer contentsComposer,
            RenderOptions options,
            PdfRenderWorkspace workspace) throws IOException {
        PdfUnitRenderer unitRenderer = new PdfUnitRenderer(fontCatalog);
        List<PdfUnitResult> results = new ArrayList<>(document.sequences().size());
        Map<String, String> sourceTargets = new LinkedHashMap<>();
        for (PdfSequencePlan sequence : document.sequences()) {
            List<BlockNode> blocks = sequenceBlocks(
                    sequence, entries, pagination.targetPhysicalPages(), contentsComposer);
            PdfRenderIds ids = PdfRenderIds.augment(document.renderIds(), blocks);
            PdfUnitResult result = unitRenderer.render(
                    document, sequence, blocks, pagination, ids, options, workspace);
            results.add(result);
            ids.linkIds().forEach((link, sourceId) ->
                    sourceTargets.put(sourceId, link.targetId()));
        }
        return measuredPass(results, sourceTargets);
    }

    /** 目录和正文单元之间保留物理分页，随后作为完整序列一次排版。 */
    private List<BlockNode> sequenceBlocks(
            PdfSequencePlan sequence,
            List<PdfTocEntry> entries,
            Map<String, Integer> targetPages,
            PdfTableOfContentsComposer contentsComposer) {
        List<BlockNode> blocks = new ArrayList<>();
        for (int index = 0; index < sequence.units().size(); index++) {
            if (index > 0) {
                blocks.add(PageBreakNode.INSTANCE);
            }
            PdfSequenceUnit unit = sequence.units().get(index);
            if (unit.kind() == PdfSequenceUnit.Kind.BODY) {
                blocks.addAll(unit.blocks());
            } else {
                blocks.addAll(contentsComposer.composeBlocks(
                        unit.tableOfContents(), entries, targetPages));
            }
        }
        return List.copyOf(blocks);
    }

    /** 把局部坐标换算为下一轮目录使用的一基全局物理页码。 */
    private RenderPass measuredPass(
            List<PdfUnitResult> results, Map<String, String> sourceTargets) {
        List<Integer> physicalPages = new ArrayList<>(results.size());
        Map<String, Integer> targetPages = new LinkedHashMap<>();
        int offset = 0;
        for (PdfUnitResult result : results) {
            physicalPages.add(result.pageCount());
            for (Map.Entry<String, PdfLayoutSnapshot.Position> target
                    : result.snapshot().targets().entrySet()) {
                targetPages.put(target.getKey(), offset + target.getValue().pageIndex() + 1);
            }
            offset = Math.addExact(offset, result.pageCount());
        }
        return new RenderPass(results, physicalPages, targetPages, sourceTargets);
    }

    /** 下一轮开始前释放旧结果，工作区只保留当前有效轮次。 */
    private void discard(
            List<PdfUnitResult> results, PdfRenderWorkspace workspace) throws IOException {
        IOException failure = null;
        for (PdfUnitResult result : results) {
            try {
                workspace.discard(result.file());
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** 目录是否存在已经在文档模型层完成唯一性校验。 */
    private boolean hasTableOfContents(PdfDocumentPlan document) {
        return document.sequences().stream()
                .anyMatch(sequence -> sequence.tableOfContentsIndex() >= 0);
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

    /** 保存一轮排版结果和由同一轮测得的全局语义。 */
    private static final class RenderPass {

        /** 与页面序列顺序一致的中间 PDF。 */
        private final List<PdfUnitResult> results;

        /** 每个页面序列的物理页数。 */
        private final List<Integer> physicalPages;

        /** 所有可见目标的一基物理页码。 */
        private final Map<String, Integer> targetPages;

        /** 当前轮次使用的内部链接源目标映射。 */
        private final Map<String, String> sourceTargets;

        /** 冻结一轮已经完成排版的结果。 */
        private RenderPass(
                List<PdfUnitResult> results,
                List<Integer> physicalPages,
                Map<String, Integer> targetPages,
                Map<String, String> sourceTargets) {
            this.results = List.copyOf(results);
            this.physicalPages = List.copyOf(physicalPages);
            this.targetPages = Map.copyOf(targetPages);
            this.sourceTargets = Map.copyOf(sourceTargets);
        }

        /** @return 当前轮次的页面序列文件 */
        private List<PdfUnitResult> results() {
            return results;
        }

        /** @return 当前轮次的序列物理页数 */
        private List<Integer> physicalPages() {
            return physicalPages;
        }

        /** @return 当前轮次的目标物理页码 */
        private Map<String, Integer> targetPages() {
            return targetPages;
        }

        /** @return 当前轮次的链接源目标映射 */
        private Map<String, String> sourceTargets() {
            return sourceTargets;
        }
    }
}
