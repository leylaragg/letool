package io.github.leylaragg.letool.print.render;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.TablePageBreakPolicy;
import io.github.leylaragg.letool.print.document.style.TableStyle;
import io.github.leylaragg.letool.print.document.style.TextWrapMode;
import io.github.leylaragg.letool.print.document.style.WhitespaceMode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.EnumSet;
import java.util.Set;

/**
 * 某个文档渲染器能够处理的节点类型与文档特性集合。
 *
 * <p>能力对象不可变且线程安全；节点检查复用文档模型的唯一遍历实现。</p>
 *
 * @author leyland
 */
public final class OutputCapability {

    /** 渲染器支持的节点具体类型。 */
    private final Set<Class<? extends DocumentNode>> supportedNodeTypes;

    /** 渲染器支持的公共文档特性。 */
    private final Set<DocumentFeature> supportedFeatures;

    /**
     * 创建输出能力。
     *
     * @param supportedNodeTypes 非空支持类型集合
     */
    public OutputCapability(Set<Class<? extends DocumentNode>> supportedNodeTypes) {
        this(supportedNodeTypes, Set.of());
    }

    /**
     * 创建节点和文档特性能力快照。
     *
     * @param supportedNodeTypes 非空支持类型集合
     * @param supportedFeatures 支持的公共文档特性
     */
    public OutputCapability(Set<Class<? extends DocumentNode>> supportedNodeTypes,
            Set<DocumentFeature> supportedFeatures) {
        this.supportedNodeTypes = Set.copyOf(supportedNodeTypes);
        this.supportedFeatures = Set.copyOf(supportedFeatures);
        if (this.supportedNodeTypes.isEmpty()) {
            throw new IllegalArgumentException("supportedNodeTypes 不能为空");
        }
    }

    /**
     * 判断是否支持具体节点。
     *
     * @param node 文档节点
     * @return 节点具体类型已声明时返回 {@code true}
     */
    public boolean supports(DocumentNode node) {
        return node != null && supportedNodeTypes.contains(node.getClass());
    }

    /**
     * 判断是否支持一个公共文档特性。
     *
     * @param feature 文档特性
     * @return 已声明支持时返回 {@code true}
     */
    public boolean supports(DocumentFeature feature) {
        return feature != null && supportedFeatures.contains(feature);
    }

    /**
     * 要求支持文档中的每个节点。
     *
     * @param document 通用文档模型
     * @throws PrintValidationException 发现不支持节点时抛出
     */
    public void requireSupports(DocumentModel document) {
        for (DocumentNode node : DocumentTraversal.depthFirst(document)) {
            if (!supports(node)) {
                throw PrintValidationException.invalidDocument(
                        "输出实现不支持节点类型：" + node.getClass().getSimpleName());
            }
        }
        for (DocumentFeature feature : detectFeatures(document)) {
            if (!supports(feature)) {
                throw PrintValidationException.invalidDocument(
                        "输出实现不支持文档特性：" + feature);
            }
        }
    }

    /** 从有效文档中提取需要输出实现理解的非基础特性。 */
    private Set<DocumentFeature> detectFeatures(DocumentModel document) {
        EnumSet<DocumentFeature> features = EnumSet.noneOf(DocumentFeature.class);
        if (document.pageSequences().size() > 1) {
            features.add(DocumentFeature.MULTIPLE_PAGE_SEQUENCES);
        }
        if (document.styleSheet().hasNamedStyles()) {
            features.add(DocumentFeature.NAMED_STYLES);
        }
        for (PageSequence sequence : document.pageSequences()) {
            if (!sequence.header().isEmpty()) {
                features.add(DocumentFeature.PAGE_HEADER);
            }
            if (!sequence.footer().isEmpty()) {
                features.add(DocumentFeature.PAGE_FOOTER);
            }
            if (!sequence.pageNumbering().includedInCount()
                    || sequence.pageNumbering().restartAt().isPresent()) {
                features.add(DocumentFeature.LOGICAL_PAGE_NUMBERING);
            }
        }
        for (DocumentNode node : DocumentTraversal.depthFirst(document)) {
            detectNodeFeatures(document, node, features);
        }
        return Set.copyOf(features);
    }

    /** 补充只有结合节点实际样式才能确定的特性。 */
    private void detectNodeFeatures(
            DocumentModel document, DocumentNode node, Set<DocumentFeature> features) {
        if (node instanceof ParagraphNode paragraph) {
            detectParagraphFeatures(
                    document.styleSheet().resolveParagraph(paragraph.styleName()), features);
        } else if (node instanceof HeadingNode heading) {
            detectParagraphFeatures(
                    document.styleSheet().resolveParagraph(heading.styleName()), features);
        } else if (node instanceof TableNode table) {
            TableStyle style = document.styleSheet().resolveTable(table.styleName());
            if (style.repeatHeader()) {
                features.add(DocumentFeature.REPEATED_TABLE_HEADER);
            }
            if (style.pageBreakPolicy() != TablePageBreakPolicy.AUTO) {
                features.add(DocumentFeature.TABLE_PAGE_BREAK_POLICY);
            }
        }
    }

    /** 非默认空白和折行需要输出实现主动解释。 */
    private void detectParagraphFeatures(
            ParagraphStyle style, Set<DocumentFeature> features) {
        if (style.whitespaceMode() != WhitespaceMode.COLLAPSE
                || style.textWrapMode() != TextWrapMode.NORMAL) {
            features.add(DocumentFeature.TEXT_FLOW_CONTROL);
        }
    }
}
