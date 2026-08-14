package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.DocumentNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.ImageNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.TableCell;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableRow;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 对可信标签返回的完整核心节点树执行中央容量和循环 ID 治理。
 *
 * @author leyland
 */
final class ExtensionNodeGovernor {

    /** 禁止实例化治理工具。 */
    private ExtensionNodeGovernor() {
    }

    /**
     * 迭代统计扩展返回树，不使用 Java 调用栈。
     *
     * @param root 扩展返回的根节点
     * @param governor 单次绑定中央计数器
     * @param idsAllowed 当前词法位置是否允许稳定 ID
     */
    static void govern(
            DocumentNode root, BindingGovernor governor, boolean idsAllowed) {
        Deque<Object> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Object current = pending.pop();
            if (current instanceof DocumentNode node) {
                governor.addNodes(1);
                if (!idsAllowed && !node.id().isEmpty()) {
                    throw PrintValidationException.invalidDocument(
                            "循环后代的自定义标签不能生成节点 ID");
                }
                pushDocumentChildren(pending, node, governor);
            } else if (current instanceof TableRow row) {
                governor.addNodes(1);
                pushReverse(pending, row.cells());
            } else if (current instanceof TableCell cell) {
                governor.addNodes(1);
                pushReverse(pending, cell.content());
            }
        }
    }

    /** 统计当前文档节点文本并压入直接子节点。 */
    private static void pushDocumentChildren(
            Deque<Object> pending, DocumentNode node, BindingGovernor governor) {
        if (node instanceof TextNode text) {
            governor.addText(text.text().length());
        } else if (node instanceof BookmarkNode bookmark) {
            governor.addText(bookmark.label().length());
        } else if (node instanceof ImageNode image) {
            governor.addText(image.altText().length());
        } else if (node instanceof SectionNode section) {
            pushReverse(pending, section.children());
        } else if (node instanceof HeadingNode heading) {
            pushReverse(pending, heading.children());
        } else if (node instanceof ParagraphNode paragraph) {
            pushReverse(pending, paragraph.children());
        } else if (node instanceof InternalLinkNode link) {
            pushReverse(pending, link.label());
        } else if (node instanceof TableNode table) {
            pushReverse(pending, table.rows());
        }
    }

    /** 反向压栈以保持自然文档顺序。 */
    private static void pushReverse(Deque<Object> pending, java.util.List<?> values) {
        for (int index = values.size() - 1; index >= 0; index--) {
            pending.push(values.get(index));
        }
    }
}
