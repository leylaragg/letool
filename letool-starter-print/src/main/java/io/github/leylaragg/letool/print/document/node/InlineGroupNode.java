package io.github.leylaragg.letool.print.document.node;

import java.util.List;

/**
 * 行内组合；下划线按最终排版的每行文字宽度绘制。
 *
 * @param children 行内子节点，构建时复制为不可变列表
 * @param underline 是否按最终文字行宽绘制下划线
 * @param widthPoints 声明宽度，单位为点，范围为 0 到 2000；0 表示由内容决定
 * @param keepTogether 是否保持组合完整，并在组合之间换行
 * @author leyland
 * @date 2026/10/14
 */
public record InlineGroupNode(List<InlineNode> children, boolean underline, double widthPoints,
        boolean keepTogether) implements InlineNode {
    /**
     * 创建宽度由内容决定的行内组合。
     *
     * @param children 行内子节点，构建时复制为不可变列表
     * @param underline 是否按最终文字行宽绘制下划线
     * @throws NullPointerException 子节点列表或元素为 null
     */
    public InlineGroupNode(List<InlineNode> children, boolean underline) {
        this(children, underline, 0, false);
    }

    /** 保留已有固定宽度组合的构造语义。 */
    public InlineGroupNode(List<InlineNode> children, boolean underline, double widthPoints) {
        this(children, underline, widthPoints, false);
    }

    /** 将间隔和后续内容等组合绑定为同一换行单位，避免行末留下孤立间隔。 */
    public InlineGroupNode withKeepTogether() {
        return new InlineGroupNode(children, underline, widthPoints, true);
    }

    /**
     * 创建并校验不可变节点。
     *
     * @param children 行内子节点，构建时复制为不可变列表
     * @param underline 是否按最终文字行宽绘制下划线
     * @param widthPoints 声明宽度，单位为点，范围为 0 到 2000；0 表示由内容决定
     * @param keepTogether 是否保持组合完整
     * @throws NullPointerException 子节点列表或元素为 null
     * @throws IllegalArgumentException 宽度非有限或超出范围
     */
    public InlineGroupNode {
        children = List.copyOf(children);
        if (!Double.isFinite(widthPoints) || widthPoints < 0 || widthPoints > 2000) {
            throw new IllegalArgumentException("行内宽度无效");
        }
    }

    /** @return 空字符串，本节点不建立独立导航目标 */
    @Override
    public String id() {
        return "";
    }
}
