package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.BlockNode;

import java.util.List;
import java.util.Objects;

/**
 * PDF 分段排版使用的局部视图，文档级语义仍以完整模型为准。
 *
 * @author leyland
 */
final class PdfRenderView {

    private final DocumentModel document;
    private final List<BlockNode> blocks;

    /** 保存完整文档引用，并冻结当前分段的正文节点。 */
    private PdfRenderView(DocumentModel document, List<? extends BlockNode> blocks) {
        this.document = Objects.requireNonNull(document, "document 不能为空");
        this.blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks 不能为空"));
    }

    /** @return 覆盖完整正文的渲染视图 */
    static PdfRenderView complete(DocumentModel document) {
        return new PdfRenderView(document, document.pageSequences().get(0).body());
    }

    /**
     * @param document 已通过全局校验的完整文档
     * @param blocks 当前物理分段的正文
     * @return 不重复执行文档级校验的局部视图
     */
    static PdfRenderView segment(DocumentModel document, List<? extends BlockNode> blocks) {
        return new PdfRenderView(document, blocks);
    }

    /** @return 提供元数据、样式和全局导航语义的完整文档 */
    DocumentModel document() {
        return document;
    }

    /** @return 当前分段沿用的物理页面布局 */
    PageLayout pageLayout() {
        return document.pageSequences().get(0).pageLayout();
    }

    /** @return 当前分段实际参与排版的正文节点 */
    List<BlockNode> blocks() {
        return blocks;
    }
}
