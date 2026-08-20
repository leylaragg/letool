package io.github.leylaragg.letool.print.document.style;

import java.util.Objects;
import java.util.Optional;

/**
 * 单元格边框、背景、间距和垂直对齐的不可变样式。
 *
 * @author leyland
 */
public final class CellStyle {

    /** 框架默认单元格样式。 */
    private static final CellStyle DEFAULT = builder().build();

    /** 上边框。 */
    private final CellBorder topBorder;

    /** 右边框。 */
    private final CellBorder rightBorder;

    /** 下边框。 */
    private final CellBorder bottomBorder;

    /** 左边框。 */
    private final CellBorder leftBorder;

    /** 可选背景色。 */
    private final DocumentColor background;

    /** 单元格内边距。 */
    private final BoxSpacing padding;

    /** 垂直对齐方式。 */
    private final VerticalAlignment verticalAlignment;

    /** 从 Builder 创建单元格样式。 */
    private CellStyle(Builder builder) {
        this.topBorder = Objects.requireNonNull(builder.topBorder, "topBorder 不能为空");
        this.rightBorder = Objects.requireNonNull(builder.rightBorder, "rightBorder 不能为空");
        this.bottomBorder = Objects.requireNonNull(builder.bottomBorder, "bottomBorder 不能为空");
        this.leftBorder = Objects.requireNonNull(builder.leftBorder, "leftBorder 不能为空");
        this.background = builder.background;
        this.padding = Objects.requireNonNull(builder.padding, "padding 不能为空");
        this.verticalAlignment = Objects.requireNonNull(
                builder.verticalAlignment, "verticalAlignment 不能为空");
    }

    /** @return 新的单元格样式 Builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 框架默认单元格样式 */
    public static CellStyle defaults() {
        return DEFAULT;
    }

    /** @return 上边框 */
    public CellBorder topBorder() {
        return topBorder;
    }

    /** @return 右边框 */
    public CellBorder rightBorder() {
        return rightBorder;
    }

    /** @return 下边框 */
    public CellBorder bottomBorder() {
        return bottomBorder;
    }

    /** @return 左边框 */
    public CellBorder leftBorder() {
        return leftBorder;
    }

    /** @return 可选背景色 */
    public Optional<DocumentColor> background() {
        return Optional.ofNullable(background);
    }

    /** @return 单元格内边距 */
    public BoxSpacing padding() {
        return padding;
    }

    /** @return 垂直对齐方式 */
    public VerticalAlignment verticalAlignment() {
        return verticalAlignment;
    }

    /** 同一组单元格属性代表同一个样式值。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CellStyle that)) {
            return false;
        }
        return topBorder.equals(that.topBorder)
                && rightBorder.equals(that.rightBorder)
                && bottomBorder.equals(that.bottomBorder)
                && leftBorder.equals(that.leftBorder)
                && Objects.equals(background, that.background)
                && padding.equals(that.padding)
                && verticalAlignment == that.verticalAlignment;
    }

    /** @return 与单元格样式值一致的哈希码 */
    @Override
    public int hashCode() {
        return Objects.hash(topBorder, rightBorder, bottomBorder, leftBorder,
                background, padding, verticalAlignment);
    }

    /**
     * 单元格样式构建器。
     *
     * @author leyland
     */
    public static final class Builder {

        /** 默认上边框。 */
        private CellBorder topBorder = CellBorder.none();

        /** 默认右边框。 */
        private CellBorder rightBorder = CellBorder.none();

        /** 默认下边框。 */
        private CellBorder bottomBorder = CellBorder.none();

        /** 默认左边框。 */
        private CellBorder leftBorder = CellBorder.none();

        /** 默认无背景色。 */
        private DocumentColor background;

        /** 默认无内边距。 */
        private BoxSpacing padding = BoxSpacing.zero();

        /** 默认顶端对齐。 */
        private VerticalAlignment verticalAlignment = VerticalAlignment.TOP;

        /**
         * 一次设置四条边框。
         *
         * @param border 四边统一边框
         * @return 当前 Builder
         */
        public Builder borders(CellBorder border) {
            this.topBorder = border;
            this.rightBorder = border;
            this.bottomBorder = border;
            this.leftBorder = border;
            return this;
        }

        /**
         * 设置上边框。
         *
         * @param topBorder 上边框
         * @return 当前 Builder
         */
        public Builder topBorder(CellBorder topBorder) {
            this.topBorder = topBorder;
            return this;
        }

        /**
         * 设置右边框。
         *
         * @param rightBorder 右边框
         * @return 当前 Builder
         */
        public Builder rightBorder(CellBorder rightBorder) {
            this.rightBorder = rightBorder;
            return this;
        }

        /**
         * 设置下边框。
         *
         * @param bottomBorder 下边框
         * @return 当前 Builder
         */
        public Builder bottomBorder(CellBorder bottomBorder) {
            this.bottomBorder = bottomBorder;
            return this;
        }

        /**
         * 设置左边框。
         *
         * @param leftBorder 左边框
         * @return 当前 Builder
         */
        public Builder leftBorder(CellBorder leftBorder) {
            this.leftBorder = leftBorder;
            return this;
        }

        /**
         * 设置单元格背景色。
         *
         * @param background 背景色；{@code null} 表示无背景
         * @return 当前 Builder
         */
        public Builder background(DocumentColor background) {
            this.background = background;
            return this;
        }

        /**
         * 设置单元格内边距。
         *
         * @param padding 单元格内边距
         * @return 当前 Builder
         */
        public Builder padding(BoxSpacing padding) {
            this.padding = padding;
            return this;
        }

        /**
         * 设置垂直对齐方式。
         *
         * @param verticalAlignment 垂直对齐方式
         * @return 当前 Builder
         */
        public Builder verticalAlignment(VerticalAlignment verticalAlignment) {
            this.verticalAlignment = verticalAlignment;
            return this;
        }

        /** @return 不可变单元格样式 */
        public CellStyle build() {
            return new CellStyle(this);
        }
    }
}
