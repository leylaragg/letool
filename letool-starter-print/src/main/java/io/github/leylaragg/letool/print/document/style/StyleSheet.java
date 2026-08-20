package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 文档可引用的强类型命名样式快照。
 *
 * @author leyland
 */
public final class StyleSheet {

    /** 单份文档允许的命名样式总数。 */
    private static final int MAX_NAMED_STYLES = 4_096;

    /** 空样式表共享实例。 */
    private static final StyleSheet EMPTY = new Builder().build();

    /** 命名文本样式。 */
    private final Map<String, TextStyle> textStyles;

    /** 命名段落样式。 */
    private final Map<String, ParagraphStyle> paragraphStyles;

    /** 命名表格样式。 */
    private final Map<String, TableStyle> tableStyles;

    /** 命名单元格样式。 */
    private final Map<String, CellStyle> cellStyles;

    /** 从 Builder 创建样式表快照。 */
    private StyleSheet(Builder builder) {
        this.textStyles = Map.copyOf(builder.textStyles);
        this.paragraphStyles = Map.copyOf(builder.paragraphStyles);
        this.tableStyles = Map.copyOf(builder.tableStyles);
        this.cellStyles = Map.copyOf(builder.cellStyles);
        validateSize();
        validateReferences();
    }

    /** @return 新的样式表 Builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 没有命名样式的框架默认样式表 */
    public static StyleSheet empty() {
        return EMPTY;
    }

    /** @return 框架默认文本样式 */
    public TextStyle defaultTextStyle() {
        return TextStyle.defaults();
    }

    /** @return 框架默认段落样式 */
    public ParagraphStyle defaultParagraphStyle() {
        return ParagraphStyle.defaults();
    }

    /** @return 框架默认表格样式 */
    public TableStyle defaultTableStyle() {
        return TableStyle.defaults();
    }

    /** @return 框架默认单元格样式 */
    public CellStyle defaultCellStyle() {
        return CellStyle.defaults();
    }

    /** @return 是否包含任意命名样式 */
    public boolean hasNamedStyles() {
        return !(textStyles.isEmpty() && paragraphStyles.isEmpty()
                && tableStyles.isEmpty() && cellStyles.isEmpty());
    }

    /**
     * 查找命名文本样式。
     *
     * @param name 文本样式名
     * @return 可选文本样式
     */
    public Optional<TextStyle> text(String name) {
        return Optional.ofNullable(textStyles.get(name));
    }

    /**
     * 查找命名段落样式。
     *
     * @param name 段落样式名
     * @return 可选段落样式
     */
    public Optional<ParagraphStyle> paragraph(String name) {
        return Optional.ofNullable(paragraphStyles.get(name));
    }

    /**
     * 查找命名表格样式。
     *
     * @param name 表格样式名
     * @return 可选表格样式
     */
    public Optional<TableStyle> table(String name) {
        return Optional.ofNullable(tableStyles.get(name));
    }

    /**
     * 查找命名单元格样式。
     *
     * @param name 单元格样式名
     * @return 可选单元格样式
     */
    public Optional<CellStyle> cell(String name) {
        return Optional.ofNullable(cellStyles.get(name));
    }

    /**
     * 解析节点引用的文本样式。
     *
     * @param name 可选文本样式名
     * @return 命名样式或框架默认文本样式
     */
    public TextStyle resolveText(String name) {
        return resolve(name, textStyles, TextStyle.defaults(), "文本");
    }

    /**
     * 解析节点引用的段落样式。
     *
     * @param name 可选段落样式名
     * @return 命名样式或框架默认段落样式
     */
    public ParagraphStyle resolveParagraph(String name) {
        return resolve(name, paragraphStyles, ParagraphStyle.defaults(), "段落");
    }

    /**
     * 解析节点引用的表格样式。
     *
     * @param name 可选表格样式名
     * @return 命名样式或框架默认表格样式
     */
    public TableStyle resolveTable(String name) {
        return resolve(name, tableStyles, TableStyle.defaults(), "表格");
    }

    /**
     * 解析节点引用的单元格样式。
     *
     * @param name 可选单元格样式名
     * @return 命名样式或框架默认单元格样式
     */
    public CellStyle resolveCell(String name) {
        return resolve(name, cellStyles, CellStyle.defaults(), "单元格");
    }

    /** @return 不可修改的命名文本样式 */
    public Map<String, TextStyle> textStyles() {
        return textStyles;
    }

    /** @return 不可修改的命名段落样式 */
    public Map<String, ParagraphStyle> paragraphStyles() {
        return paragraphStyles;
    }

    /** @return 不可修改的命名表格样式 */
    public Map<String, TableStyle> tableStyles() {
        return tableStyles;
    }

    /** @return 不可修改的命名单元格样式 */
    public Map<String, CellStyle> cellStyles() {
        return cellStyles;
    }

    /** 防止模板通过大量未使用样式放大模型。 */
    private void validateSize() {
        long size = (long) textStyles.size() + paragraphStyles.size()
                + tableStyles.size() + cellStyles.size();
        if (size > MAX_NAMED_STYLES) {
            throw PrintValidationException.invalidDocument(
                    "命名样式数量不能超过 " + MAX_NAMED_STYLES);
        }
    }

    /** 段落默认文本样式必须在同一份样式表中存在。 */
    private void validateReferences() {
        paragraphStyles.forEach((name, style) -> {
            if (!style.textStyleName().isEmpty() && !textStyles.containsKey(style.textStyleName())) {
                throw PrintValidationException.invalidDocument(
                        "段落样式引用的文本样式不存在：" + style.textStyleName());
            }
        });
    }

    /** 解析一个类型明确的样式引用。 */
    private <T> T resolve(String name, Map<String, T> styles, T defaultStyle, String type) {
        String normalized = StyleNames.optional(name);
        if (normalized.isEmpty()) {
            return defaultStyle;
        }
        T style = styles.get(normalized);
        if (style == null) {
            throw PrintValidationException.invalidDocument(
                    type + "样式不存在：" + normalized);
        }
        return style;
    }

    /**
     * 样式表构建器。
     *
     * @author leyland
     */
    public static final class Builder {

        /** 待冻结的文本样式。 */
        private final Map<String, TextStyle> textStyles = new LinkedHashMap<>();

        /** 待冻结的段落样式。 */
        private final Map<String, ParagraphStyle> paragraphStyles = new LinkedHashMap<>();

        /** 待冻结的表格样式。 */
        private final Map<String, TableStyle> tableStyles = new LinkedHashMap<>();

        /** 待冻结的单元格样式。 */
        private final Map<String, CellStyle> cellStyles = new LinkedHashMap<>();

        /**
         * 注册一个文本样式。
         *
         * @param name 样式名
         * @param style 文本样式
         * @return 当前 Builder
         */
        public Builder text(String name, TextStyle style) {
            put(textStyles, name, style);
            return this;
        }

        /**
         * 注册一个段落样式。
         *
         * @param name 样式名
         * @param style 段落样式
         * @return 当前 Builder
         */
        public Builder paragraph(String name, ParagraphStyle style) {
            put(paragraphStyles, name, style);
            return this;
        }

        /**
         * 注册一个表格样式。
         *
         * @param name 样式名
         * @param style 表格样式
         * @return 当前 Builder
         */
        public Builder table(String name, TableStyle style) {
            put(tableStyles, name, style);
            return this;
        }

        /**
         * 注册一个单元格样式。
         *
         * @param name 样式名
         * @param style 单元格样式
         * @return 当前 Builder
         */
        public Builder cell(String name, CellStyle style) {
            put(cellStyles, name, style);
            return this;
        }

        /** @return 不可变样式表 */
        public StyleSheet build() {
            return new StyleSheet(this);
        }

        /** 注册单类样式并立即拒绝重名。 */
        private <T> void put(Map<String, T> target, String name, T style) {
            String normalized = StyleNames.required(name);
            Objects.requireNonNull(style, "style 不能为空");
            if (target.putIfAbsent(normalized, style) != null) {
                throw PrintValidationException.invalidDocument("样式名称重复：" + normalized);
            }
        }
    }
}
