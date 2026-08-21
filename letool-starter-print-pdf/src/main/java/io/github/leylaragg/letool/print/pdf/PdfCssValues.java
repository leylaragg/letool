package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.style.BorderLineStyle;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.DocumentLength;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.document.style.TableLayoutMode;
import io.github.leylaragg.letool.print.document.style.TextAlignment;
import io.github.leylaragg.letool.print.document.style.VerticalAlignment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 把已经校验的文档样式值转换为不受区域设置影响的 CSS。
 *
 * @author leyland
 */
final class PdfCssValues {

    /** 十六进制颜色使用的固定大写字符。 */
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** 禁止实例化 CSS 值转换工具。 */
    private PdfCssValues() {
    }

    /** @return 文档长度对应的 CSS 长度 */
    static String length(DocumentLength length) {
        Objects.requireNonNull(length, "length 不能为空");
        String unit = switch (length.unit()) {
            case MILLIMETER -> "mm";
            case POINT -> "pt";
            case PERCENT -> "%";
        };
        return decimal(length.value()) + unit;
    }

    /** @return 微米对应的厘米值 */
    static String micrometers(long value) {
        return BigDecimal.valueOf(value).movePointLeft(4)
                .stripTrailingZeros().toPlainString() + "cm";
    }

    /** @return RGB 颜色对应的六位十六进制值 */
    static String color(DocumentColor color) {
        Objects.requireNonNull(color, "color 不能为空");
        char[] output = {'#', '0', '0', '0', '0', '0', '0'};
        writeHex(output, 1, color.red());
        writeHex(output, 3, color.green());
        writeHex(output, 5, color.blue());
        return new String(output);
    }

    /** @return CSS 使用的数值字重 */
    static String fontWeight(FontWeight weight) {
        return switch (Objects.requireNonNull(weight, "weight 不能为空")) {
            case NORMAL -> "400";
            case BOLD -> "700";
        };
    }

    /** @return CSS 水平对齐值 */
    static String alignment(TextAlignment alignment) {
        return switch (Objects.requireNonNull(alignment, "alignment 不能为空")) {
            case LEFT -> "left";
            case CENTER -> "center";
            case RIGHT -> "right";
            case JUSTIFY -> "justify";
        };
    }

    /** @return CSS 表格布局值 */
    static String tableLayout(TableLayoutMode mode) {
        return switch (Objects.requireNonNull(mode, "mode 不能为空")) {
            case AUTO -> "auto";
            case FIXED -> "fixed";
        };
    }

    /** @return CSS 垂直对齐值 */
    static String verticalAlignment(VerticalAlignment alignment) {
        return switch (Objects.requireNonNull(alignment, "alignment 不能为空")) {
            case TOP -> "top";
            case MIDDLE -> "middle";
            case BOTTOM -> "bottom";
        };
    }

    /** @return CSS 边框线型 */
    static String borderStyle(BorderLineStyle style) {
        return switch (Objects.requireNonNull(style, "style 不能为空")) {
            case NONE -> "none";
            case SOLID -> "solid";
            case DASHED -> "dashed";
            case DOTTED -> "dotted";
            case DOUBLE -> "double";
        };
    }

    /** 把有限 double 转换为普通十进制，不读取 JVM Locale。 */
    static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** 写入一个颜色分量的两位十六进制值。 */
    private static void writeHex(char[] output, int offset, int value) {
        output[offset] = HEX[value >>> 4];
        output[offset + 1] = HEX[value & 0x0F];
    }
}
