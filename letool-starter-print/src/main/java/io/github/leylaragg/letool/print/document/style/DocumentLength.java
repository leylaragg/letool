package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 跨输出共享的样式长度；除首行缩进外，数值必须非负。
 *
 * @author leyland
 */
public final class DocumentLength {

    /** 长度单位。 */
    public enum Unit {
        /** 物理毫米。 */
        MILLIMETER,
        /** 排版点。 */
        POINT,
        /** 相对于所属区域的百分比。 */
        PERCENT,
        /** 固定表格中占据剩余列宽，不用于其他样式长度。 */
        AUTO
    }

    /** 允许的毫米和点数上限，防止异常尺寸进入渲染器。 */
    private static final double MAX_ABSOLUTE_VALUE = 10_000;

    /** 长度数值。 */
    private final double value;

    /** 长度单位。 */
    private final Unit unit;

    /** 创建经过公共范围校验的长度。 */
    private DocumentLength(double value, Unit unit) {
        this(value, unit, false);
    }

    /** 仅首行缩进允许负值，其绝对值仍受通用长度上限约束。 */
    private DocumentLength(double value, Unit unit, boolean signedIndent) {
        if (!Double.isFinite(value) || value < 0 && !signedIndent) {
            throw PrintValidationException.invalidDocument("样式长度必须是非负有限值");
        }
        double maximum = unit == Unit.PERCENT ? 100 : MAX_ABSOLUTE_VALUE;
        if (Math.abs(value) > maximum) {
            throw PrintValidationException.invalidDocument("样式长度超出允许范围");
        }
        this.value = value;
        this.unit = unit;
    }

    /**
     * 创建毫米长度。
     *
     * @param value 毫米数
     * @return 毫米长度
     */
    public static DocumentLength millimeters(double value) {
        return new DocumentLength(value, Unit.MILLIMETER);
    }

    /**
     * 创建允许负值的首行缩进，用于悬挂对齐。
     *
     * @param value 缩进毫米数，负值表示悬挂缩进
     * @return 毫米缩进长度
     * @throws PrintValidationException 值非有限或绝对值超出长度上限
     */
    public static DocumentLength indentMillimeters(double value) {
        return new DocumentLength(value, Unit.MILLIMETER, true);
    }

    /**
     * 创建排版点长度。
     *
     * @param value 点数
     * @return 点长度
     */
    public static DocumentLength points(double value) {
        return new DocumentLength(value, Unit.POINT);
    }

    /** @return 固定表格中分配剩余宽度的自动列声明 */
    public static DocumentLength autoColumn() {
        return new DocumentLength(0, Unit.AUTO);
    }

    /**
     * 创建百分比长度。
     *
     * @param value 百分比数值
     * @return 百分比长度
     */
    public static DocumentLength percent(double value) {
        return new DocumentLength(value, Unit.PERCENT);
    }

    /** @return 长度数值 */
    public double value() {
        return value;
    }

    /** @return 长度单位 */
    public Unit unit() {
        return unit;
    }

    /** 要求当前长度大于零。 */
    void requirePositive(String property) {
        if (value == 0) {
            throw PrintValidationException.invalidDocument(property + "必须大于零");
        }
    }

    /** 要求当前长度使用指定单位集合。 */
    void requireUnit(String property, Unit... units) {
        for (Unit allowed : units) {
            if (unit == allowed) {
                return;
            }
        }
        throw PrintValidationException.invalidDocument(property + "使用了不支持的长度单位");
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof DocumentLength that
                && Double.compare(value, that.value) == 0 && unit == that.unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unit);
    }

    @Override
    public String toString() {
        return "DocumentLength[value=" + value + ", unit=" + unit + "]";
    }
}
