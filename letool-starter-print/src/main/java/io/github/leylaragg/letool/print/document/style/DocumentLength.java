package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 跨输出共享的非负样式长度。
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
        PERCENT
    }

    /** 允许的毫米和点数上限，防止异常尺寸进入渲染器。 */
    private static final double MAX_ABSOLUTE_VALUE = 10_000;

    /** 长度数值。 */
    private final double value;

    /** 长度单位。 */
    private final Unit unit;

    /** 创建经过公共范围校验的长度。 */
    private DocumentLength(double value, Unit unit) {
        if (!Double.isFinite(value) || value < 0) {
            throw PrintValidationException.invalidDocument("样式长度必须是非负有限值");
        }
        double maximum = unit == Unit.PERCENT ? 100 : MAX_ABSOLUTE_VALUE;
        if (value > maximum) {
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
     * 创建排版点长度。
     *
     * @param value 点数
     * @return 点长度
     */
    public static DocumentLength points(double value) {
        return new DocumentLength(value, Unit.POINT);
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
