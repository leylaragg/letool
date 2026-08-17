package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.document.DocumentModel;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ObjectFactory;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 保存一次 DOCX 渲染使用的可变对象，避免请求状态进入渲染器字段。
 *
 * @author leyland
 */
final class DocxRenderContext {

    /** 当前请求独享的 OOXML 包。 */
    private final WordprocessingMLPackage wordPackage;

    /** 当前请求创建 WordprocessingML 元素所用的工厂。 */
    private final ObjectFactory factory;

    /** 宿主传入的不可变渲染配置。 */
    private final DocxRendererOptions options;

    /** 当前文档的安全书签映射。 */
    private final DocxRenderIds renderIds;

    /** 当前请求正在渲染的不可变文档。 */
    private final DocumentModel document;

    /** 产物中是否含有需要编辑器更新的域。 */
    private boolean fieldUpdateRequired;

    /** 按名称排序保存发生过的降级类型。 */
    private final Set<String> degradedNodeTypes = new TreeSet<>();

    /** 本次渲染发生的降级节点总数。 */
    private long degradedNodeCount;

    /**
     * 创建请求上下文。
     *
     * @param wordPackage 当前请求的 OOXML 包
     * @param options DOCX 渲染配置
     * @param renderIds 当前文档的书签映射
     * @param document 当前请求的文档模型
     */
    DocxRenderContext(
            WordprocessingMLPackage wordPackage,
            DocxRendererOptions options,
            DocxRenderIds renderIds,
            DocumentModel document) {
        this.wordPackage = Objects.requireNonNull(wordPackage, "wordPackage 不能为空");
        this.factory = new ObjectFactory();
        this.options = Objects.requireNonNull(options, "options 不能为空");
        this.renderIds = Objects.requireNonNull(renderIds, "renderIds 不能为空");
        this.document = Objects.requireNonNull(document, "document 不能为空");
    }

    /** @return 当前请求的 OOXML 包 */
    WordprocessingMLPackage wordPackage() {
        return wordPackage;
    }

    /** @return WordprocessingML 对象工厂 */
    ObjectFactory factory() {
        return factory;
    }

    /** @return DOCX 渲染配置 */
    DocxRendererOptions options() {
        return options;
    }

    /** @return 当前文档的安全书签映射 */
    DocxRenderIds renderIds() {
        return renderIds;
    }

    /** @return 当前请求的文档模型 */
    DocumentModel document() {
        return document;
    }

    /** 标记产物包含需要 Word 或 WPS 更新的域。 */
    void requireFieldUpdate() {
        this.fieldUpdateRequired = true;
    }

    /** @return 是否需要编辑器更新域 */
    boolean fieldUpdateRequired() {
        return fieldUpdateRequired;
    }

    /** 记录一个已经实际写入替代表达的节点。 */
    void recordDegradation(String nodeType) {
        degradedNodeTypes.add(nodeType);
        degradedNodeCount++;
    }

    /** @return 降级节点总数 */
    long degradedNodeCount() {
        return degradedNodeCount;
    }

    /** @return 按名称稳定排序的降级节点类型 */
    Set<String> degradedNodeTypes() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(degradedNodeTypes));
    }
}
