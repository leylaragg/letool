package io.github.leylaragg.letool.print.document.node;

/**
 * 可出现在标题、段落或链接标签中的行内节点。
 *
 * @author leyland
 */
public sealed interface InlineNode extends DocumentNode
        permits TextNode, BookmarkNode, InternalLinkNode {
}
