package io.github.leylaragg.letool.print.document.node;

import java.util.Objects;

/**
 * 受控原生向量，不接受 SVG、脚本或任意绘图程序。
 *
 * @param kind 受控几何类型
 * @param widthPoints 外框宽度，单位为点，范围大于 0 且不超过 2000
 * @param heightPoints 外框高度，单位为点，范围大于 0 且不超过 2000
 * @param strokePoints 线宽，单位为点，范围大于 0 且不超过 20
 * @param selected 是否绘制圆内点或方框叉号
 * @param cells 连续 U 格的格数，范围为 1 到 200
 * @param markPoints 圆内点直径、方框叉号边长或底线距底部的距离，单位为点；其余类型不使用
 * @author leyland
 * @date 2026/10/14
 */
public record ShapeNode(Kind kind, double widthPoints, double heightPoints,
    double strokePoints, boolean selected, int cells, double markPoints) implements InlineNode {
    /** 各形状共享受控几何参数，具体尺寸由宿主或模板给出。 */
    public enum Kind {
        /** 外圆与可选内点。 */
        CIRCLE,
        /** 方框与可选叉号。 */
        SQUARE,
        /** 共用竖边的连续 U 格。 */
        U_CELLS,
        /** 书写区域内的水平底线。 */
        UNDERLINE,
        /** 只占位、不绘制的间距。 */
        SPACE
    }

    /**
     * 创建并校验受控几何参数。
     *
     * @param kind 受控几何类型
     * @param widthPoints 外框宽度，单位为点，范围大于 0 且不超过 2000
     * @param heightPoints 外框高度，单位为点，范围大于 0 且不超过 2000
     * @param strokePoints 线宽，单位为点，范围大于 0 且不超过 20
     * @param selected 是否绘制圆内点或方框叉号
     * @param cells 连续 U 格的格数，范围为 1 到 200
     * @param markPoints 圆内点直径、方框叉号边长或底线距底部的距离，单位为点；其余类型不使用
     * @throws NullPointerException 几何类型为 null
     * @throws IllegalArgumentException 尺寸非有限、超出范围或标记尺寸超过外框
     */
    public ShapeNode {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (!Double.isFinite(widthPoints) || widthPoints <= 0 || widthPoints > 2000
            || !Double.isFinite(heightPoints) || heightPoints <= 0 || heightPoints > 2000
            || !Double.isFinite(strokePoints) || strokePoints <= 0 || strokePoints > 20
            || !Double.isFinite(markPoints) || markPoints < 0
            || cells < 1 || cells > 200) {
            throw new IllegalArgumentException("向量尺寸或格数无效");
        }

        // 底线偏移受高度约束，其他标记必须同时落在外框的宽度和高度内。
        double limit = kind == Kind.UNDERLINE ? heightPoints : Math.min(widthPoints, heightPoints);
        if (markPoints > limit) {
            throw new IllegalArgumentException("标识尺寸超过向量声明范围");
        }
    }

    /** @return 空字符串，几何节点不建立独立导航目标 */
    @Override
    public String id() {
        return "";
    }
}
