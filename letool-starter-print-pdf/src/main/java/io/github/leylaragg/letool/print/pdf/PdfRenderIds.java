package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 为一次 PDF 渲染建立稳定且不写回文档模型的布局 ID。
 *
 * @author leyland
 */
final class PdfRenderIds {

    private static final String HEADING_PREFIX = "letool-toc-heading-";
    private static final String LINK_PREFIX = "letool-internal-link-";

    private final Map<HeadingNode, String> headingIds;
    private final Map<InternalLinkNode, String> linkIds;
    private final Set<String> targetIds;

    /**
     * 按文档遍历顺序分配自动 ID。
     *
     * @param document 完整文档
     * @return 本次渲染使用的 ID 快照
     */
    static PdfRenderIds create(DocumentModel document) {
        Objects.requireNonNull(document, "document 不能为空");
        return create(DocumentTraversal.depthFirst(document));
    }

    /**
     * 为目录展开产生的新节点补充分配 ID，同时保留文档级 ID。
     *
     * @param base 完整文档建立的稳定 ID
     * @param blocks 当前页面序列实际排版的块节点
     * @return 覆盖原节点和受控生成节点的 ID 快照
     */
    static PdfRenderIds augment(PdfRenderIds base, List<BlockNode> blocks) {
        Objects.requireNonNull(base, "base 不能为空");
        Objects.requireNonNull(blocks, "blocks 不能为空");
        ListState state = new ListState(base);
        List<DocumentNode> nodes = DocumentTraversal.depthFirstBlocks(blocks);
        for (DocumentNode node : nodes) {
            if (!node.id().isEmpty()) {
                state.usedIds.add(node.id());
                state.targetIds.add(node.id());
            }
        }
        for (DocumentNode node : nodes) {
            if (node instanceof HeadingNode heading && !state.headingIds.containsKey(heading)) {
                String id = heading.id().isEmpty()
                        ? state.nextAvailable(HEADING_PREFIX, true) : heading.id();
                state.headingIds.put(heading, id);
                state.targetIds.add(id);
            } else if (node instanceof InternalLinkNode link
                    && !state.linkIds.containsKey(link)) {
                state.linkIds.put(link, state.nextAvailable(LINK_PREFIX, false));
            }
        }
        return new PdfRenderIds(state);
    }

    /** 按稳定遍历结果建立最终 ID 快照。 */
    private static PdfRenderIds create(Iterable<DocumentNode> nodes) {
        ListState state = new ListState();
        for (DocumentNode node : nodes) {
            if (!node.id().isEmpty()) {
                state.usedIds.add(node.id());
                state.targetIds.add(node.id());
            }
        }
        for (DocumentNode node : nodes) {
            if (node instanceof HeadingNode heading) {
                String id = heading.id().isEmpty()
                        ? state.nextAvailable(HEADING_PREFIX, true) : heading.id();
                state.headingIds.put(heading, id);
                state.targetIds.add(id);
            } else if (node instanceof InternalLinkNode link) {
                state.linkIds.put(link, state.nextAvailable(LINK_PREFIX, false));
            }
        }
        return new PdfRenderIds(state);
    }

    private PdfRenderIds(ListState state) {
        this.headingIds = Collections.unmodifiableMap(new IdentityHashMap<>(state.headingIds));
        this.linkIds = Collections.unmodifiableMap(new IdentityHashMap<>(state.linkIds));
        this.targetIds = Set.copyOf(state.targetIds);
    }

    /** 返回标题的显式或自动布局目标 ID。 */
    String targetId(HeadingNode heading) {
        String id = headingIds.get(heading);
        if (id == null) {
            throw new IllegalArgumentException("标题不属于当前渲染文档");
        }
        return id;
    }

    /** 返回按节点身份分配的链接源 ID。 */
    String sourceId(InternalLinkNode link) {
        String id = linkIds.get(link);
        if (id == null) {
            throw new IllegalArgumentException("链接不属于当前渲染文档");
        }
        return id;
    }

    /** @return 文档中需要提取位置的全部目标 ID */
    Set<String> targetIds() {
        return targetIds;
    }

    /** @return 链接节点与源布局 ID 的只读身份映射 */
    Map<InternalLinkNode, String> linkIds() {
        return linkIds;
    }

    /** 构建阶段的可变状态不会逃逸到最终快照。 */
    private static final class ListState {
        private final Set<String> usedIds = new LinkedHashSet<>();
        private final Set<String> targetIds = new LinkedHashSet<>();
        private final Map<HeadingNode, String> headingIds = new IdentityHashMap<>();
        private final Map<InternalLinkNode, String> linkIds = new IdentityHashMap<>();
        private int nextHeading = 1;
        private int nextLink = 1;

        /** 创建空状态，用于完整文档首次分配。 */
        private ListState() {
        }

        /** 从文档级快照继续分配目录生成节点，原映射按身份保留。 */
        private ListState(PdfRenderIds base) {
            usedIds.addAll(base.targetIds);
            usedIds.addAll(base.linkIds.values());
            targetIds.addAll(base.targetIds);
            headingIds.putAll(base.headingIds);
            linkIds.putAll(base.linkIds);
        }

        /** 逐个跳过用户 ID 和此前自动 ID，保持结果稳定。 */
        private String nextAvailable(String prefix, boolean heading) {
            int number = heading ? nextHeading : nextLink;
            String candidate;
            do {
                candidate = prefix + number++;
            } while (!usedIds.add(candidate));
            if (heading) {
                nextHeading = number;
            } else {
                nextLink = number;
            }
            return candidate;
        }
    }
}
