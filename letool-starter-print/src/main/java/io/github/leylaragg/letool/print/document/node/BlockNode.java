package io.github.leylaragg.letool.print.document.node;

/**
 * 可直接出现在文档或表格单元格中的块级节点。
 *
 * @author leyland
 */
public sealed interface BlockNode extends DocumentNode
        permits SectionNode, HeadingNode, ParagraphNode, TableNode, ImageNode, PageBreakNode,
        AnnotationNode, TableOfContentsNode {
}
