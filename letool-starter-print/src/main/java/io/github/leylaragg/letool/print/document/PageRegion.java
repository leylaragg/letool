package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.BlockNode;

import java.util.List;

/**
 * 页面序列中重复出现的页眉或页脚内容。
 *
 * @author leyland
 */
public final class PageRegion {

    /** 空页面区域共享实例。 */
    private static final PageRegion EMPTY = new PageRegion(List.of());

    /** 不可修改的区域块节点。 */
    private final List<BlockNode> blocks;

    /**
     * 创建页面区域快照。
     *
     * @param blocks 区域块节点，允许为空
     */
    public PageRegion(List<BlockNode> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    /** @return 空页面区域 */
    public static PageRegion empty() {
        return EMPTY;
    }

    /** @return 不可修改的区域块节点 */
    public List<BlockNode> blocks() {
        return blocks;
    }

    /** @return 区域是否没有内容 */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
