package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.PageCountNode;
import io.github.leylaragg.letool.print.document.node.PageNumberNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.document.style.TableStyle;
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

    /** 单份文档允许的页面序列数量。 */
    private static final int MAX_PAGE_SEQUENCES = 256;

    /** 可选文档元数据。 */
    private final DocumentMetadata metadata;

    /** 文档命名样式表。 */
    private final StyleSheet styleSheet;

    /** 不可修改的页面序列。 */
    private final List<PageSequence> pageSequences;

    /**
     * 创建文档模型快照。
     *
     * @param metadata 文档元数据
     * @param styleSheet 文档命名样式表
     * @param pageSequences 页面序列，至少包含一个元素
     * @throws NullPointerException 任一参数或节点为 {@code null} 时抛出
     * @throws PrintValidationException 文档树违反公共模型约束时抛出
     */
    public DocumentModel(
            DocumentMetadata metadata,
            StyleSheet styleSheet,
            List<PageSequence> pageSequences) {
        this.metadata = Objects.requireNonNull(metadata, "metadata 不能为空");
        this.styleSheet = Objects.requireNonNull(styleSheet, "styleSheet 不能为空");
        this.pageSequences = List.copyOf(pageSequences);
        if (this.pageSequences.isEmpty() || this.pageSequences.size() > MAX_PAGE_SEQUENCES) {
            throw PrintValidationException.invalidDocument(
                    "页面序列数量必须在 1 到 " + MAX_PAGE_SEQUENCES + " 之间");
        }
        validateDocument();
    }

    /**
     * 创建使用空样式表和一个普通页面序列的文档。
     *
     * @param metadata 文档元数据
     * @param pageLayout 页面布局
     * @param body 正文块节点
     * @return 已完成整体验证的单序列文档
     */
    public static DocumentModel singleSequence(
            DocumentMetadata metadata, PageLayout pageLayout, List<BlockNode> body) {
        return new DocumentModel(metadata, StyleSheet.empty(),
                List.of(PageSequence.body(pageLayout, body)));
    }

    /** @return 文档元数据 */
    public DocumentMetadata metadata() {
        return metadata;
    }

    /** @return 文档命名样式表 */
    public StyleSheet styleSheet() {
        return styleSheet;
    }

    /** @return 不可修改的页面序列 */
    public List<PageSequence> pageSequences() {
        return pageSequences;
    }

    /** 创建快照时完成所有跨节点验证，后续调用链只消费有效模型。 */
    private void validateDocument() {
        List<DocumentNode> nodes = DocumentTraversal.depthFirst(this);
        validateRegions();
        validatePageNumbering();
        validateStyles(nodes);
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

    /** 页眉页脚只接受可安全重复的内容，并且不能产生重复导航目标。 */
    private void validateRegions() {
        for (PageSequence sequence : pageSequences) {
            validateRegion(sequence.header(), "页眉");
            validateRegion(sequence.footer(), "页脚");
        }
    }

    /** 校验单个重复区域的递归内容。 */
    private void validateRegion(PageRegion region, String regionName) {
        for (DocumentNode node : DocumentTraversal.depthFirstBlocks(region.blocks())) {
            if (!node.id().isEmpty()) {
                throw PrintValidationException.invalidDocument(regionName + "节点不能声明逻辑 ID");
            }
            if (node instanceof HeadingNode || node instanceof TableOfContentsNode
                    || node instanceof AnnotationNode || node instanceof PageBreakNode) {
                throw PrintValidationException.invalidDocument(
                        regionName + "不能包含 " + node.getClass().getSimpleName());
            }
        }
    }

    /** 未参与逻辑计数的序列没有可显示的当前页码。 */
    private void validatePageNumbering() {
        for (PageSequence sequence : pageSequences) {
            if (sequence.pageNumbering().includedInCount()) {
                continue;
            }
            boolean hasCurrentPage = DocumentTraversal.depthFirst(sequence).stream()
                    .anyMatch(PageNumberNode.class::isInstance);
            if (hasCurrentPage) {
                throw PrintValidationException.invalidDocument("未计入页码的页面序列不能显示当前页码");
            }
        }
    }

    /** 解析所有节点样式，并补充需要结合节点结构判断的表格约束。 */
    private void validateStyles(List<DocumentNode> nodes) {
        for (DocumentNode node : nodes) {
            if (node instanceof TextNode text) {
                styleSheet.resolveText(text.styleName());
            } else if (node instanceof PageNumberNode pageNumber) {
                styleSheet.resolveText(pageNumber.styleName());
            } else if (node instanceof PageCountNode pageCount) {
                styleSheet.resolveText(pageCount.styleName());
            } else if (node instanceof ParagraphNode paragraph) {
                styleSheet.resolveParagraph(paragraph.styleName());
            } else if (node instanceof HeadingNode heading) {
                styleSheet.resolveParagraph(heading.styleName());
            } else if (node instanceof TableNode table) {
                validateTableStyle(table);
            }
        }
    }

    /** 表格样式需要与已经完成网格校验的表格结构一致。 */
    private void validateTableStyle(TableNode table) {
        TableStyle style = styleSheet.resolveTable(table.styleName());
        if (!style.columnWidths().isEmpty()
                && style.columnWidths().size() != table.effectiveColumnCount()) {
            throw PrintValidationException.invalidDocument("表格列宽数量与有效列数不一致");
        }
        if (style.repeatHeader() && table.headerRowCount() == 0) {
            throw PrintValidationException.invalidDocument("重复表头样式要求表格声明表头行");
        }
        for (TableRow row : table.rows()) {
            for (TableCell cell : row.cells()) {
                styleSheet.resolveCell(cell.styleName());
            }
        }
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
        long rootContents = pageSequences.stream()
                .flatMap(sequence -> sequence.body().stream())
                .filter(TableOfContentsNode.class::isInstance)
                .count();
        if (rootContents != 1) {
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
        } else if (inline instanceof PageNumberNode || inline instanceof PageCountNode) {
            text.append('0');
        }
    }
}
