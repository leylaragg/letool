package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

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
     * 校验整棵文档树的 ID 唯一性以及链接、批注引用完整性。
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
            } else if (node instanceof AnnotationNode annotation) {
                targets.add(annotation.targetId());
            }
        }
        for (String target : targets) {
            if (!ids.contains(target)) {
                throw PrintValidationException.invalidDocument("文档引用目标不存在：" + target);
            }
        }
        validateTableOfContents(nodes);
    }

    /** 校验目录只在根部出现一次，并且声明位置之后确实有可显示标题。 */
    private void validateTableOfContents(List<DocumentNode> nodes) {
        List<TableOfContentsNode> contents = nodes.stream()
                .filter(TableOfContentsNode.class::isInstance)
                .map(TableOfContentsNode.class::cast)
                .toList();
        if (contents.size() > 1) {
            throw PrintValidationException.invalidDocument("文档最多只能声明一个目录");
        }
        if (contents.isEmpty()) {
            return;
        }
        TableOfContentsNode tableOfContents = contents.get(0);
        if (blocks.stream().filter(TableOfContentsNode.class::isInstance).count() != 1) {
            throw PrintValidationException.invalidDocument("目录只能位于文档根节点");
        }
        boolean afterContents = false;
        boolean foundHeading = false;
        for (DocumentNode node : nodes) {
            if (node == tableOfContents) {
                afterContents = true;
                continue;
            }
            if (!afterContents || !(node instanceof HeadingNode heading)
                    || heading.level() < tableOfContents.minLevel()
                    || heading.level() > tableOfContents.maxLevel()) {
                continue;
            }
            if (visibleHeadingText(heading).isBlank()) {
                throw PrintValidationException.invalidDocument("目录标题内容不能为空");
            }
            foundHeading = true;
        }
        if (!foundHeading) {
            throw PrintValidationException.invalidDocument("目录声明之后没有可收录标题");
        }
    }

    /** 提取标题最终呈现的文字，不带入链接或书签的导航属性。 */
    private String visibleHeadingText(HeadingNode heading) {
        StringBuilder text = new StringBuilder();
        heading.children().forEach(child -> appendVisibleText(text, child));
        return text.toString();
    }

    /** 按行内节点顺序拼接用户真正能看到的标题内容。 */
    private void appendVisibleText(StringBuilder text, InlineNode inline) {
        if (inline instanceof TextNode value) {
            text.append(value.text());
        } else if (inline instanceof BookmarkNode bookmark) {
            text.append(bookmark.label());
        } else if (inline instanceof InternalLinkNode link) {
            link.label().forEach(child -> appendVisibleText(text, child));
        }
    }
}
