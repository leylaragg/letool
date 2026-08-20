package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 单元格一侧的边框语义。
 *
 * @author leyland
 */
public final class CellBorder {

    /** 无边框的共享值。 */
    private static final CellBorder NONE = new CellBorder(
            BorderLineStyle.NONE, DocumentLength.points(0), DocumentColor.BLACK);

    /** 边框线型。 */
    private final BorderLineStyle lineStyle;

    /** 边框宽度。 */
    private final DocumentLength width;

    /** 边框颜色。 */
    private final DocumentColor color;

    /** 创建一侧边框。 */
    private CellBorder(BorderLineStyle lineStyle, DocumentLength width, DocumentColor color) {
        this.lineStyle = Objects.requireNonNull(lineStyle, "lineStyle 不能为空");
        this.width = Objects.requireNonNull(width, "width 不能为空");
        this.color = Objects.requireNonNull(color, "color 不能为空");
        width.requireUnit("边框宽度", DocumentLength.Unit.MILLIMETER, DocumentLength.Unit.POINT);
        if (lineStyle == BorderLineStyle.NONE && width.value() != 0) {
            throw PrintValidationException.invalidDocument("无边框的宽度必须为零");
        }
        if (lineStyle != BorderLineStyle.NONE) {
            width.requirePositive("边框宽度");
        }
    }

    /** @return 无边框 */
    public static CellBorder none() {
        return NONE;
    }

    /**
     * 创建可见边框。
     *
     * @param lineStyle 非 {@code NONE} 线型
     * @param width 边框宽度
     * @param color 边框颜色
     * @return 单元格边框
     */
    public static CellBorder of(
            BorderLineStyle lineStyle, DocumentLength width, DocumentColor color) {
        if (lineStyle == BorderLineStyle.NONE) {
            throw PrintValidationException.invalidDocument("可见边框不能使用 NONE 线型");
        }
        return new CellBorder(lineStyle, width, color);
    }

    /** @return 边框线型 */
    public BorderLineStyle lineStyle() {
        return lineStyle;
    }

    /** @return 边框宽度 */
    public DocumentLength width() {
        return width;
    }

    /** @return 边框颜色 */
    public DocumentColor color() {
        return color;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof CellBorder that
                && lineStyle == that.lineStyle && width.equals(that.width) && color.equals(that.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineStyle, width, color);
    }
}
