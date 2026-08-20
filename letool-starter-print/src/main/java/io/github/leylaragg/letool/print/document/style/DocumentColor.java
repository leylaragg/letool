package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 不依赖 CSS 表达式的 RGB 文档颜色。
 *
 * @author leyland
 */
public final class DocumentColor {

    /** 框架默认黑色。 */
    public static final DocumentColor BLACK = new DocumentColor(0, 0, 0);

    /** 框架默认白色。 */
    public static final DocumentColor WHITE = new DocumentColor(255, 255, 255);

    /** 红色分量。 */
    private final int red;

    /** 绿色分量。 */
    private final int green;

    /** 蓝色分量。 */
    private final int blue;

    /** 创建已校验的 RGB 颜色。 */
    private DocumentColor(int red, int green, int blue) {
        if (!valid(red) || !valid(green) || !valid(blue)) {
            throw PrintValidationException.invalidDocument("RGB 颜色分量必须在 0 到 255 之间");
        }
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    /**
     * 创建 RGB 颜色。
     *
     * @param red 红色分量
     * @param green 绿色分量
     * @param blue 蓝色分量
     * @return 文档颜色
     */
    public static DocumentColor rgb(int red, int green, int blue) {
        return new DocumentColor(red, green, blue);
    }

    /** @return 红色分量 */
    public int red() {
        return red;
    }

    /** @return 绿色分量 */
    public int green() {
        return green;
    }

    /** @return 蓝色分量 */
    public int blue() {
        return blue;
    }

    /** 判断单个颜色分量是否有效。 */
    private static boolean valid(int value) {
        return value >= 0 && value <= 255;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof DocumentColor that
                && red == that.red && green == that.green && blue == that.blue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(red, green, blue);
    }

    @Override
    public String toString() {
        return "DocumentColor[red=" + red + ", green=" + green + ", blue=" + blue + "]";
    }
}
