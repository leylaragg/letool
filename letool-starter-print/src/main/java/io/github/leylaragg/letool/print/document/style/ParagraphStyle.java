package io.github.leylaragg.letool.print.document.style;

import java.util.Objects;

/**
 * 段落排版、空白和折行规则的不可变样式。
 *
 * @author leyland
 */
public final class ParagraphStyle {

    /** 框架默认段落样式。 */
    private static final ParagraphStyle DEFAULT = builder().build();

    /** 可选默认文本样式名。 */
    private final String textStyleName;

    /** 水平对齐。 */
    private final TextAlignment alignment;

    /** 首行缩进。 */
    private final DocumentLength firstLineIndent;

    /** 左缩进。 */
    private final DocumentLength leftIndent;

    /** 右缩进。 */
    private final DocumentLength rightIndent;

    /** 段前间距。 */
    private final DocumentLength spacingBefore;

    /** 段后间距。 */
    private final DocumentLength spacingAfter;

    /** 空白处理方式。 */
    private final WhitespaceMode whitespaceMode;

    /** 文字折行方式。 */
    private final TextWrapMode textWrapMode;

    /** 是否优先保持整段同页。 */
    private final boolean keepTogether;

    /** 从 Builder 创建段落样式。 */
    private ParagraphStyle(Builder builder) {
        this.textStyleName = StyleNames.optional(builder.textStyleName);
        this.alignment = Objects.requireNonNull(builder.alignment, "alignment 不能为空");
        this.firstLineIndent = requireMillimeters(builder.firstLineIndent, "首行缩进");
        this.leftIndent = requireMillimeters(builder.leftIndent, "左缩进");
        this.rightIndent = requireMillimeters(builder.rightIndent, "右缩进");
        this.spacingBefore = requireMillimeters(builder.spacingBefore, "段前间距");
        this.spacingAfter = requireMillimeters(builder.spacingAfter, "段后间距");
        this.whitespaceMode = Objects.requireNonNull(builder.whitespaceMode, "whitespaceMode 不能为空");
        this.textWrapMode = Objects.requireNonNull(builder.textWrapMode, "textWrapMode 不能为空");
        this.keepTogether = builder.keepTogether;
    }

    /** @return 新的段落样式 Builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 框架默认段落样式 */
    public static ParagraphStyle defaults() {
        return DEFAULT;
    }

    /** @return 默认文本样式名，空字符串表示使用框架默认文本样式 */
    public String textStyleName() {
        return textStyleName;
    }

    /** @return 水平对齐 */
    public TextAlignment alignment() {
        return alignment;
    }

    /** @return 首行缩进 */
    public DocumentLength firstLineIndent() {
        return firstLineIndent;
    }

    /** @return 左缩进 */
    public DocumentLength leftIndent() {
        return leftIndent;
    }

    /** @return 右缩进 */
    public DocumentLength rightIndent() {
        return rightIndent;
    }

    /** @return 段前间距 */
    public DocumentLength spacingBefore() {
        return spacingBefore;
    }

    /** @return 段后间距 */
    public DocumentLength spacingAfter() {
        return spacingAfter;
    }

    /** @return 空白处理方式 */
    public WhitespaceMode whitespaceMode() {
        return whitespaceMode;
    }

    /** @return 文字折行方式 */
    public TextWrapMode textWrapMode() {
        return textWrapMode;
    }

    /** @return 是否优先保持整段同页 */
    public boolean keepTogether() {
        return keepTogether;
    }

    /** 同一组段落属性代表同一个样式值。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParagraphStyle that)) {
            return false;
        }
        return keepTogether == that.keepTogether
                && textStyleName.equals(that.textStyleName)
                && alignment == that.alignment
                && firstLineIndent.equals(that.firstLineIndent)
                && leftIndent.equals(that.leftIndent)
                && rightIndent.equals(that.rightIndent)
                && spacingBefore.equals(that.spacingBefore)
                && spacingAfter.equals(that.spacingAfter)
                && whitespaceMode == that.whitespaceMode
                && textWrapMode == that.textWrapMode;
    }

    /** @return 与段落样式值一致的哈希码 */
    @Override
    public int hashCode() {
        return Objects.hash(textStyleName, alignment, firstLineIndent, leftIndent, rightIndent,
                spacingBefore, spacingAfter, whitespaceMode, textWrapMode, keepTogether);
    }

    /** 段落间距统一使用毫米，避免输出端解释相对尺寸。 */
    private static DocumentLength requireMillimeters(DocumentLength value, String property) {
        DocumentLength required = Objects.requireNonNull(value, property + "不能为空");
        required.requireUnit(property, DocumentLength.Unit.MILLIMETER);
        return required;
    }

    /**
     * 段落样式构建器。
     *
     * @author leyland
     */
    public static final class Builder {

        /** 默认使用框架文本样式。 */
        private String textStyleName = "";

        /** 默认左对齐。 */
        private TextAlignment alignment = TextAlignment.LEFT;

        /** 默认无首行缩进。 */
        private DocumentLength firstLineIndent = DocumentLength.millimeters(0);

        /** 默认无左缩进。 */
        private DocumentLength leftIndent = DocumentLength.millimeters(0);

        /** 默认无右缩进。 */
        private DocumentLength rightIndent = DocumentLength.millimeters(0);

        /** 默认无段前间距。 */
        private DocumentLength spacingBefore = DocumentLength.millimeters(0);

        /** 默认无段后间距。 */
        private DocumentLength spacingAfter = DocumentLength.millimeters(0);

        /** 默认折叠空白。 */
        private WhitespaceMode whitespaceMode = WhitespaceMode.COLLAPSE;

        /** 默认正常折行。 */
        private TextWrapMode textWrapMode = TextWrapMode.NORMAL;

        /** 默认允许段落自然分页。 */
        private boolean keepTogether;

        /**
         * 设置段落采用的默认文本样式。
         *
         * @param textStyleName 默认文本样式名
         * @return 当前 Builder
         */
        public Builder textStyleName(String textStyleName) {
            this.textStyleName = textStyleName;
            return this;
        }

        /**
         * 设置水平对齐方式。
         *
         * @param alignment 水平对齐方式
         * @return 当前 Builder
         */
        public Builder alignment(TextAlignment alignment) {
            this.alignment = alignment;
            return this;
        }

        /**
         * 设置首行缩进。
         *
         * @param firstLineIndent 首行缩进
         * @return 当前 Builder
         */
        public Builder firstLineIndent(DocumentLength firstLineIndent) {
            this.firstLineIndent = firstLineIndent;
            return this;
        }

        /**
         * 设置左缩进。
         *
         * @param leftIndent 左缩进
         * @return 当前 Builder
         */
        public Builder leftIndent(DocumentLength leftIndent) {
            this.leftIndent = leftIndent;
            return this;
        }

        /**
         * 设置右缩进。
         *
         * @param rightIndent 右缩进
         * @return 当前 Builder
         */
        public Builder rightIndent(DocumentLength rightIndent) {
            this.rightIndent = rightIndent;
            return this;
        }

        /**
         * 设置段前间距。
         *
         * @param spacingBefore 段前间距
         * @return 当前 Builder
         */
        public Builder spacingBefore(DocumentLength spacingBefore) {
            this.spacingBefore = spacingBefore;
            return this;
        }

        /**
         * 设置段后间距。
         *
         * @param spacingAfter 段后间距
         * @return 当前 Builder
         */
        public Builder spacingAfter(DocumentLength spacingAfter) {
            this.spacingAfter = spacingAfter;
            return this;
        }

        /**
         * 设置空白处理方式。
         *
         * @param whitespaceMode 空白处理方式
         * @return 当前 Builder
         */
        public Builder whitespaceMode(WhitespaceMode whitespaceMode) {
            this.whitespaceMode = whitespaceMode;
            return this;
        }

        /**
         * 设置文字折行方式。
         *
         * @param textWrapMode 文字折行方式
         * @return 当前 Builder
         */
        public Builder textWrapMode(TextWrapMode textWrapMode) {
            this.textWrapMode = textWrapMode;
            return this;
        }

        /**
         * 设置是否优先保持整段同页。
         *
         * @param keepTogether 是否优先保持整段同页
         * @return 当前 Builder
         */
        public Builder keepTogether(boolean keepTogether) {
            this.keepTogether = keepTogether;
            return this;
        }

        /** @return 不可变段落样式 */
        public ParagraphStyle build() {
            return new ParagraphStyle(this);
        }
    }
}
