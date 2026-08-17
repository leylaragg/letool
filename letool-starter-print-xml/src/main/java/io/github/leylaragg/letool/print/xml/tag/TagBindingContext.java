package io.github.leylaragg.letool.print.xml.tag;

import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.xml.extension.PrintDataView;

import java.util.List;
import java.util.Objects;

/**
 * 自定义标签计划使用的只读绑定上下文。
 *
 * @author leyland
 */
public final class TagBindingContext {

    /** 当前绑定作用域的数据视图。 */
    private final PrintDataView data;

    /** 框架已绑定的块级子节点。 */
    private final List<BlockNode> blockChildren;

    /** 框架已绑定的行内子节点。 */
    private final List<InlineNode> inlineChildren;

    /**
     * 创建标签绑定上下文。
     *
     * @param data 只读数据视图
     * @param blockChildren 块级子节点
     * @param inlineChildren 行内子节点
     */
    public TagBindingContext(
            PrintDataView data,
            List<? extends BlockNode> blockChildren,
            List<? extends InlineNode> inlineChildren) {
        this.data = Objects.requireNonNull(data, "data 不能为空");
        this.blockChildren = List.copyOf(
                Objects.requireNonNull(blockChildren, "blockChildren 不能为空"));
        this.inlineChildren = List.copyOf(
                Objects.requireNonNull(inlineChildren, "inlineChildren 不能为空"));
    }

    /** @return 当前只读数据视图 */
    public PrintDataView data() {
        return data;
    }

    /** @return 不可修改的块级子节点 */
    public List<BlockNode> blockChildren() {
        return blockChildren;
    }

    /** @return 不可修改的行内子节点 */
    public List<InlineNode> inlineChildren() {
        return inlineChildren;
    }
}
