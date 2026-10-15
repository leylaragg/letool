package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;

/**
 * 收集目录标题，并把目录声明展开为固定表格视图。
 *
 * @author leyland
 */
final class PdfTableOfContentsComposer {

    static final int MAX_ENTRIES = 10_000;

    /** 目录只收录声明之后、层级范围内的标题。 */
    List<PdfTocEntry> collect(DocumentModel document, PdfRenderIds ids) {
        TableOfContentsNode contents = DocumentTraversal.depthFirst(document).stream()
            .filter(TableOfContentsNode.class::isInstance)
            .map(TableOfContentsNode.class::cast)
            .findFirst().orElseThrow();
        if (!contents.bookmarkRoot().isEmpty()) {
            return collectBookmarks(document, contents, ids);
        }
        boolean afterContents = false;
        List<PdfTocEntry> entries = new ArrayList<>();
        for (DocumentNode node : DocumentTraversal.depthFirst(document)) {
            if (node == contents) {
                afterContents = true;
            } else if (afterContents && node instanceof HeadingNode heading
                && heading.level() >= contents.minLevel()
                && heading.level() <= contents.maxLevel()) {
                if (entries.size() >= MAX_ENTRIES) {
                    throw PrintValidationException.invalidDocument("PDF 目录条目不能超过 10,000 个");
                }
                entries.add(new PdfTocEntry(
                    heading.level(), visibleText(heading.children()), ids.targetId(heading)));
            }
        }
        return List.copyOf(entries);
    }

    /** 根的后代按树顺序收录，同级顺序沿用原声明顺序。 */
    private List<PdfTocEntry> collectBookmarks(DocumentModel document, TableOfContentsNode contents, PdfRenderIds ids) {
        Map<String, List<BookmarkNode>> children = new LinkedHashMap<>();
        for (DocumentNode node : DocumentTraversal.depthFirst(document)) {
            if (node instanceof BookmarkNode bookmark) {
                children.computeIfAbsent(bookmark.parentId(), key -> new ArrayList<>()).add(bookmark);
            }
        }
        Deque<BookmarkEntry> pending = new ArrayDeque<>();
        pushBookmarks(pending, children.getOrDefault(contents.bookmarkRoot(), List.of()), 1);
        List<PdfTocEntry> entries = new ArrayList<>();
        while (!pending.isEmpty()) {
            BookmarkEntry entry = pending.pop();
            BookmarkNode bookmark = entry.bookmark();
            if (entry.level() >= contents.minLevel() && entry.level() <= contents.maxLevel()) {
                if (entries.size() >= MAX_ENTRIES) {
                    throw PrintValidationException.invalidDocument("PDF 目录条目不能超过 10,000 个");
                }
                entries.add(new PdfTocEntry(entry.level(), bookmark.label(),
                    ids.targetId(bookmark.effectiveTargetId())));
            }
            if (entry.level() < contents.maxLevel()) {
                pushBookmarks(pending, children.getOrDefault(bookmark.id(), List.of()), entry.level() + 1);
            }
        }
        return List.copyOf(entries);
    }

    private void pushBookmarks(Deque<BookmarkEntry> pending, List<BookmarkNode> children, int level) {
        for (int index = children.size() - 1; index >= 0; index--) {
            pending.push(new BookmarkEntry(children.get(index), level));
        }
    }

    private record BookmarkEntry(BookmarkNode bookmark, int level) {
    }

    /** 把目录声明展开为可以嵌回原页面序列的普通块节点。 */
    List<BlockNode> composeBlocks(
        TableOfContentsNode contents,
        List<PdfTocEntry> entries,
        Map<String, Integer> targetPages) {
        List<BlockNode> blocks = new ArrayList<>();
        if (contents.title() != null) {
            if (contents.layout() == null) {
                blocks.add(new ParagraphNode(contents.id(), contents.styleName(),
                    List.of(new TextNode(contents.title(), ""))));
            } else {
                blocks.add(new io.github.leylaragg.letool.print.document.node.ContentsTitleNode(
                    contents.id(), contents.title(), contents.styleName(), contents.layout()));
            }
        }
        if (entries.isEmpty() && contents.emptyMessage() != null && !contents.emptyMessage().isEmpty()) {
            blocks.add(new ParagraphNode("", "", List.of(new TextNode(contents.emptyMessage()))));
        }
        if (!entries.isEmpty()) {
            if (contents.layout() == null) {
                blocks.add(table(entries, targetPages));
            } else {
                for (int index = 0; index < entries.size(); index++) {
                    PdfTocEntry entry = entries.get(index);
                    boolean keep = index + 1 < entries.size() && entries.get(index + 1).level() > entry.level();
                    blocks.add(new io.github.leylaragg.letool.print.document.node.ContentsEntryNode(
                        new InternalLinkNode(entry.targetId(), List.of(new TextNode(entry.title(),
                        contents.layout().textStyleName()))),
                        Integer.toString(targetPages.getOrDefault(entry.targetId(), 1)), entry.level(), keep,
                        contents.id(), contents.layout()));
                }
            }
        }
        return List.copyOf(blocks);
    }

    /** 两列表格让标题、点引导和页码保持稳定顺序。 */
    private TableNode table(List<PdfTocEntry> entries, Map<String, Integer> targetPages) {
        List<TableRow> rows = new ArrayList<>();
        for (PdfTocEntry entry : entries) {
            int pageNumber = targetPages.getOrDefault(entry.targetId(), 1);
            String indent = "  ".repeat(Math.max(0, entry.level() - 1));
            ParagraphNode label = new ParagraphNode("", "", List.of(new InternalLinkNode(
                entry.targetId(),
                List.of(new TextNode(indent + entry.title() + " ........", "")))));
            ParagraphNode page = new ParagraphNode(
                "", "", List.of(new TextNode(Integer.toString(pageNumber), "")));
            rows.add(new TableRow(List.of(
                new TableCell("", List.of(label), 1, 1),
                new TableCell("", List.of(page), 1, 1))));
        }
        return new TableNode("", "", 0, rows);
    }

    /** 目录标题沿用通用行内节点的可见文字语义。 */
    private String visibleText(List<InlineNode> nodes) {
        StringBuilder text = new StringBuilder();
        for (InlineNode node : nodes) {
            if (node instanceof TextNode value) {
                text.append(value.text());
            } else if (node instanceof BookmarkNode bookmark && bookmark.targetId().isEmpty()) {
                text.append(bookmark.label());
            } else if (node instanceof InternalLinkNode link) {
                text.append(visibleText(link.label()));
            }
        }
        return text.toString();
    }
}
