package io.github.leylaragg.letool.print.document.style;

import java.util.Objects;

/**
 * 单元格等矩形区域四边的物理间距。
 *
 * @author leyland
 */
public final class BoxSpacing {

    /** 四边均为零的间距。 */
    private static final BoxSpacing ZERO = all(DocumentLength.millimeters(0));

    /** 上边距。 */
    private final DocumentLength top;

    /** 右边距。 */
    private final DocumentLength right;

    /** 下边距。 */
    private final DocumentLength bottom;

    /** 左边距。 */
    private final DocumentLength left;

    /**
     * 创建四边间距。
     *
     * @param top 上边距
     * @param right 右边距
     * @param bottom 下边距
     * @param left 左边距
     */
    public BoxSpacing(DocumentLength top, DocumentLength right,
            DocumentLength bottom, DocumentLength left) {
        this.top = requireMillimeters(top, "上边距");
        this.right = requireMillimeters(right, "右边距");
        this.bottom = requireMillimeters(bottom, "下边距");
        this.left = requireMillimeters(left, "左边距");
    }

    /**
     * 创建四边相同的间距。
     *
     * @param value 四边间距
     * @return 矩形间距
     */
    public static BoxSpacing all(DocumentLength value) {
        return new BoxSpacing(value, value, value, value);
    }

    /** @return 四边均为零的间距 */
    public static BoxSpacing zero() {
        return ZERO;
    }

    /** @return 上边距 */
    public DocumentLength top() {
        return top;
    }

    /** @return 右边距 */
    public DocumentLength right() {
        return right;
    }

    /** @return 下边距 */
    public DocumentLength bottom() {
        return bottom;
    }

    /** @return 左边距 */
    public DocumentLength left() {
        return left;
    }

    /** 单元格间距只接受物理毫米。 */
    private static DocumentLength requireMillimeters(DocumentLength value, String property) {
        DocumentLength required = Objects.requireNonNull(value, property + "不能为空");
        required.requireUnit(property, DocumentLength.Unit.MILLIMETER);
        return required;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BoxSpacing that)) {
            return false;
        }
        return top.equals(that.top) && right.equals(that.right)
                && bottom.equals(that.bottom) && left.equals(that.left);
    }

    @Override
    public int hashCode() {
        return Objects.hash(top, right, bottom, left);
    }
}
