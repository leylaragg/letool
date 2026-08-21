package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.style.StyleSheet;

import java.util.List;
import java.util.Objects;

/**
 * XML 文档在绑定前已经确定的元数据、样式表和页面序列。
 *
 * @author leyland
 */
final class CompiledDocumentPlan {

    /** 不随业务数据变化的文档元数据。 */
    private final DocumentMetadata metadata;

    /** 编译期完成引用校验的命名样式表。 */
    private final StyleSheet styleSheet;

    /** 保持 XML 声明顺序的页面序列计划。 */
    private final List<CompiledPagePlan> pages;

    /** 保存文档的静态编译结果。 */
    CompiledDocumentPlan(
            DocumentMetadata metadata, StyleSheet styleSheet, List<CompiledPagePlan> pages) {
        this.metadata = Objects.requireNonNull(metadata, "metadata 不能为空");
        this.styleSheet = Objects.requireNonNull(styleSheet, "styleSheet 不能为空");
        this.pages = List.copyOf(pages);
        if (this.pages.isEmpty()) {
            throw new IllegalArgumentException("pages 不能为空");
        }
    }

    /** @return 文档元数据 */
    DocumentMetadata metadata() {
        return metadata;
    }

    /** @return 命名样式表 */
    StyleSheet styleSheet() {
        return styleSheet;
    }

    /** @return 有序页面序列计划 */
    List<CompiledPagePlan> pages() {
        return pages;
    }
}
