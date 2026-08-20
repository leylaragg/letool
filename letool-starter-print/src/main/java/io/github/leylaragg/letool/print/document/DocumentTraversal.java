package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * 通用文档树唯一的深度优先遍历实现。
 *
 * <p>遍历使用显式栈，避免恶意深层文档消耗 Java 调用栈。返回列表不可修改。</p>
 *
 * @author leyland
 */
public final class DocumentTraversal {

    /** 允许的最大节点数量。 */
    private static final int MAX_NODES = 1_000_000;

    /** 允许的最大嵌套深度。 */
    private static final int MAX_DEPTH = 128;

    /** 禁止实例化遍历工具。 */
    private DocumentTraversal() {
    }

    /**
     * 按稳定的前序深度优先顺序遍历文档节点。
     *
     * @param document 待遍历的文档模型
     * @return 不可修改的节点快照
     * @throws NullPointerException 文档为 {@code null} 时抛出
     * @throws PrintValidationException 节点数量或嵌套深度超限时抛出
     */
    public static List<DocumentNode> depthFirst(DocumentModel document) {
        Objects.requireNonNull(document, "document 不能为空");
        Deque<NodeFrame> stack = new ArrayDeque<>();
        pushDocumentRoots(stack, document.pageSequences());
        return traverse(stack);
    }

    /** 按单个页面序列的页眉、正文、页脚顺序遍历。 */
    static List<DocumentNode> depthFirst(PageSequence sequence) {
        Objects.requireNonNull(sequence, "sequence 不能为空");
        Deque<NodeFrame> stack = new ArrayDeque<>();
        pushSequenceRoots(stack, sequence);
        return traverse(stack);
    }

    /**
     * 按给定顺序遍历一组块节点及其后代。
     *
     * <p>输出实现可用它处理经过文档校验的局部视图，无需复制整份文档。</p>
     *
     * @param roots 局部视图的块节点
     * @return 不可修改的节点快照
     */
    public static List<DocumentNode> depthFirstBlocks(List<? extends BlockNode> roots) {
        Objects.requireNonNull(roots, "roots 不能为空");
        Deque<NodeFrame> stack = new ArrayDeque<>();
        pushReverse(stack, roots, 1);
        return traverse(stack);
    }

    /** 使用同一套深度和数量治理消费已经准备好的遍历栈。 */
    private static List<DocumentNode> traverse(Deque<NodeFrame> stack) {
        List<DocumentNode> result = new ArrayList<>();
        while (!stack.isEmpty()) {
            NodeFrame frame = stack.pop();
            if (frame.depth() > MAX_DEPTH) {
                throw PrintValidationException.invalidDocument("文档嵌套深度超过 " + MAX_DEPTH);
            }
            result.add(frame.node());
            if (result.size() > MAX_NODES) {
                throw PrintValidationException.invalidDocument("文档节点数超过 " + MAX_NODES);
            }
            pushChildren(stack, frame.node(), frame.depth() + 1);
        }
        return List.copyOf(result);
    }

    /** 按文档顺序将所有页面序列的根节点压入栈。 */
    private static void pushDocumentRoots(Deque<NodeFrame> stack, List<PageSequence> sequences) {
        for (int index = sequences.size() - 1; index >= 0; index--) {
            pushSequenceRoots(stack, sequences.get(index));
        }
    }

    /** 页脚先入栈、页眉后入栈，出栈时得到自然阅读顺序。 */
    private static void pushSequenceRoots(Deque<NodeFrame> stack, PageSequence sequence) {
        pushReverse(stack, sequence.footer().blocks(), 1);
        pushReverse(stack, sequence.body(), 1);
        pushReverse(stack, sequence.header().blocks(), 1);
    }

    /** 将节点的直接子节点按反向顺序入栈，以保持自然文档顺序。 */
    private static void pushChildren(Deque<NodeFrame> stack, DocumentNode node, int depth) {
        if (node instanceof SectionNode section) {
            pushReverse(stack, section.children(), depth);
        } else if (node instanceof HeadingNode heading) {
            pushReverse(stack, heading.children(), depth);
        } else if (node instanceof ParagraphNode paragraph) {
            pushReverse(stack, paragraph.children(), depth);
        } else if (node instanceof InternalLinkNode link) {
            pushReverse(stack, link.label(), depth);
        } else if (node instanceof TableNode table) {
            List<BlockNode> content = new ArrayList<>();
            for (TableRow row : table.rows()) {
                for (TableCell cell : row.cells()) {
                    content.addAll(cell.content());
                }
            }
            pushReverse(stack, content, depth);
        }
    }

    /** 将列表反向压入栈顶。 */
    private static void pushReverse(
            Deque<NodeFrame> stack,
            List<? extends DocumentNode> nodes,
            int depth) {
        for (int index = nodes.size() - 1; index >= 0; index--) {
            stack.push(new NodeFrame(nodes.get(index), depth));
        }
    }

    /** 显式遍历栈帧。 */
    private record NodeFrame(DocumentNode node, int depth) {
    }
}
