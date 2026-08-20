package io.github.leylaragg.letool.print.document.node;

/**
 * 模板明确插入且不受空白折叠影响的行内换行。
 *
 * @author leyland
 */
public final class LineBreakNode implements InlineNode {

    /** 无状态换行节点共享实例。 */
    public static final LineBreakNode INSTANCE = new LineBreakNode();

    /** 禁止重复创建无状态换行节点。 */
    private LineBreakNode() {
    }

    /** @return 空字符串，换行节点不参与逻辑定位 */
    @Override
    public String id() {
        return "";
    }
}
