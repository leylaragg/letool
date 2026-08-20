package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;
import java.util.Set;

/**
 * 字体和文字外观的不可变样式。
 *
 * @author leyland
 */
public final class TextStyle {

    /** 框架默认文本样式。 */
    private static final TextStyle DEFAULT = builder().build();

    /** 可选字体族；空字符串表示使用输出实现的受控默认字体。 */
    private final String fontFamily;

    /** 字号。 */
    private final DocumentLength fontSize;

    /** 字重。 */
    private final FontWeight fontWeight;

    /** 文字颜色。 */
    private final DocumentColor color;

    /** 相对于字号的行高倍数。 */
    private final double lineHeight;

    /** 不可修改的文本修饰。 */
    private final Set<TextDecoration> decorations;

    /** 从 Builder 创建文本样式。 */
    private TextStyle(Builder builder) {
        this.fontFamily = normalizeFontFamily(builder.fontFamily);
        this.fontSize = Objects.requireNonNull(builder.fontSize, "fontSize 不能为空");
        fontSize.requireUnit("字号", DocumentLength.Unit.POINT);
        fontSize.requirePositive("字号");
        this.fontWeight = Objects.requireNonNull(builder.fontWeight, "fontWeight 不能为空");
        this.color = Objects.requireNonNull(builder.color, "color 不能为空");
        if (!Double.isFinite(builder.lineHeight)
                || builder.lineHeight < 0.5 || builder.lineHeight > 10) {
            throw PrintValidationException.invalidDocument("行高倍数必须在 0.5 到 10 之间");
        }
        this.lineHeight = builder.lineHeight;
        this.decorations = Set.copyOf(builder.decorations);
    }

    /** @return 新的文本样式 Builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 框架默认文本样式 */
    public static TextStyle defaults() {
        return DEFAULT;
    }

    /** @return 字体族，空字符串表示使用受控默认字体 */
    public String fontFamily() {
        return fontFamily;
    }

    /** @return 字号 */
    public DocumentLength fontSize() {
        return fontSize;
    }

    /** @return 字重 */
    public FontWeight fontWeight() {
        return fontWeight;
    }

    /** @return 文字颜色 */
    public DocumentColor color() {
        return color;
    }

    /** @return 行高倍数 */
    public double lineHeight() {
        return lineHeight;
    }

    /** @return 不可修改的文本修饰 */
    public Set<TextDecoration> decorations() {
        return decorations;
    }

    /** 同一组文本属性代表同一个样式值。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TextStyle that)) {
            return false;
        }
        return Double.compare(lineHeight, that.lineHeight) == 0
                && fontFamily.equals(that.fontFamily)
                && fontSize.equals(that.fontSize)
                && fontWeight == that.fontWeight
                && color.equals(that.color)
                && decorations.equals(that.decorations);
    }

    /** @return 与文本样式值一致的哈希码 */
    @Override
    public int hashCode() {
        return Objects.hash(fontFamily, fontSize, fontWeight, color, lineHeight, decorations);
    }

    /** 规范化可选字体族，并拒绝控制字符。 */
    private static String normalizeFontFamily(String fontFamily) {
        if (fontFamily == null || fontFamily.isBlank()) {
            return "";
        }
        String normalized = fontFamily.trim();
        if (normalized.length() > 128 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw PrintValidationException.invalidDocument("字体族名称不合法");
        }
        return normalized;
    }

    /**
     * 文本样式构建器。
     *
     * @author leyland
     */
    public static final class Builder {

        /** 可选字体族。 */
        private String fontFamily = "";

        /** 默认字号为 10 点。 */
        private DocumentLength fontSize = DocumentLength.points(10);

        /** 默认常规字重。 */
        private FontWeight fontWeight = FontWeight.NORMAL;

        /** 默认黑色。 */
        private DocumentColor color = DocumentColor.BLACK;

        /** 默认行高为 1.2 倍。 */
        private double lineHeight = 1.2;

        /** 默认不带文本修饰。 */
        private Set<TextDecoration> decorations = Set.of();

        /**
         * 设置字体族。
         *
         * @param fontFamily 字体族；空白表示默认字体
         * @return 当前 Builder
         */
        public Builder fontFamily(String fontFamily) {
            this.fontFamily = fontFamily;
            return this;
        }

        /**
         * 设置字号。
         *
         * @param fontSize 字号
         * @return 当前 Builder
         */
        public Builder fontSize(DocumentLength fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        /**
         * 设置字重。
         *
         * @param fontWeight 字重
         * @return 当前 Builder
         */
        public Builder fontWeight(FontWeight fontWeight) {
            this.fontWeight = fontWeight;
            return this;
        }

        /**
         * 设置文字颜色。
         *
         * @param color 文字颜色
         * @return 当前 Builder
         */
        public Builder color(DocumentColor color) {
            this.color = color;
            return this;
        }

        /**
         * 设置行高倍数。
         *
         * @param lineHeight 行高倍数
         * @return 当前 Builder
         */
        public Builder lineHeight(double lineHeight) {
            this.lineHeight = lineHeight;
            return this;
        }

        /**
         * 设置文本修饰。
         *
         * @param decorations 文本修饰
         * @return 当前 Builder
         */
        public Builder decorations(Set<TextDecoration> decorations) {
            this.decorations = Objects.requireNonNull(decorations, "decorations 不能为空");
            return this;
        }

        /** @return 不可变文本样式 */
        public TextStyle build() {
            return new TextStyle(this);
        }
    }
}
