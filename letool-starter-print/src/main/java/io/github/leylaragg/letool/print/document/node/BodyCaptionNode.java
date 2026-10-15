package io.github.leylaragg.letool.print.document.node;

import java.util.List;

/**
 * 在各页实际正文框底之后绘制一条通用行内图例。
 *
 * @param textStyleName 图例默认文本样式名；null 或空字符串使用默认文本样式
 * @param children 图例行内内容，构建时复制为不可变列表
 * @param baselineOffsetPoints 文字基线距正文框底向下的偏移，单位为点，范围为 0 到 200
 * @author leyland
 * @date 2026/10/14
 */
public record BodyCaptionNode(String textStyleName, List<InlineNode> children,
    double baselineOffsetPoints) implements BlockNode {
    /**
     * 创建并校验不可变节点。
     *
     * @param textStyleName 图例默认文本样式名；null 或空字符串使用默认文本样式
     * @param children 图例行内内容，构建时复制为不可变列表
     * @param baselineOffsetPoints 文字基线距正文框底向下的偏移，单位为点，范围为 0 到 200
     * @throws NullPointerException 子节点列表或元素为 null
     * @throws IllegalArgumentException 偏移非有限或超出范围
     */
    public BodyCaptionNode {
        children = List.copyOf(children);
        if (!Double.isFinite(baselineOffsetPoints) || baselineOffsetPoints < 0 || baselineOffsetPoints > 200) {
            throw new IllegalArgumentException("正文图例偏移无效");
        }
        textStyleName = textStyleName == null ? "" : textStyleName;
    }
    /** @return 空字符串，本节点不建立独立导航目标 */
    @Override
    public String id() {
        return "";
    }
}
