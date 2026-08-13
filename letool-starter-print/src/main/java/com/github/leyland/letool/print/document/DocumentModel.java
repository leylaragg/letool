package com.github.leyland.letool.print.document;

import com.github.leyland.letool.print.document.node.BlockNode;
import com.github.leyland.letool.print.document.node.DocumentNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 模板展开和数据绑定完成后的不可变通用文档模型。
 *
 * <p>模型只表达跨格式稳定语义，不包含 XML、CSS、PDF、Word 或 JasperReports 类型。</p>
 *
 * @author leyland
 */
public final class DocumentModel {

    /** 可选文档元数据。 */
    private final DocumentMetadata metadata;

    /** 物理页面布局。 */
    private final PageLayout pageLayout;

    /** 不可修改的顶层块节点。 */
    private final List<BlockNode> blocks;

    /**
     * 创建文档模型快照。
     *
     * @param metadata 文档元数据
     * @param pageLayout 页面布局
     * @param blocks 顶层块节点，允许空文档
     * @throws NullPointerException 任一参数或节点为 {@code null} 时抛出
     */
    public DocumentModel(
            DocumentMetadata metadata,
            PageLayout pageLayout,
            List<BlockNode> blocks) {
        this.metadata = Objects.requireNonNull(metadata, "metadata 不能为空");
        this.pageLayout = Objects.requireNonNull(pageLayout, "pageLayout 不能为空");
        this.blocks = List.copyOf(blocks);
    }

    /** @return 文档元数据 */
    public DocumentMetadata metadata() {
        return metadata;
    }

    /** @return 页面布局 */
    public PageLayout pageLayout() {
        return pageLayout;
    }

    /** @return 不可修改的顶层块节点 */
    public List<BlockNode> blocks() {
        return blocks;
    }

    /**
     * 校验整棵文档树的 ID 唯一性和内部链接完整性。
     *
     * @throws PrintValidationException 存在重复 ID、缺失链接目标或树规模越界时抛出
     */
    public void validate() {
        List<DocumentNode> nodes = DocumentTraversal.depthFirst(this);
        Set<String> ids = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (DocumentNode node : nodes) {
            if (!node.id().isEmpty() && !ids.add(node.id())) {
                throw PrintValidationException.invalidDocument("节点 ID 重复：" + node.id());
            }
            if (node instanceof InternalLinkNode link) {
                targets.add(link.targetId());
            }
        }
        for (String target : targets) {
            if (!ids.contains(target)) {
                throw PrintValidationException.invalidDocument("内部链接目标不存在：" + target);
            }
        }
    }
}
