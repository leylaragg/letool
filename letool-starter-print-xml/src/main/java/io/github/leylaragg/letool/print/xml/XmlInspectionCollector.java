package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.ImageNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.LineBreakNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.PageCountNode;
import io.github.leylaragg.letool.print.document.node.PageNumberNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.document.style.ParagraphStyle;
import io.github.leylaragg.letool.print.document.style.TablePageBreakPolicy;
import io.github.leylaragg.letool.print.document.style.TableStyle;
import io.github.leylaragg.letool.print.document.style.TextWrapMode;
import io.github.leylaragg.letool.print.document.style.WhitespaceMode;
import io.github.leylaragg.letool.print.render.DocumentFeature;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.template.inspection.TemplateIncludeUsage;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspection;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;
import io.github.leylaragg.letool.print.template.inspection.TemplatePathUsage;
import io.github.leylaragg.letool.print.template.inspection.TemplatePathUsageKind;
import io.github.leylaragg.letool.print.template.inspection.TemplateSourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从闭合 XML 编译计划收集模板路径、引用和输出能力上界。
 *
 * @author leyland
 */
final class XmlInspectionCollector {

    /** 文档模板代码。 */
    private final String templateCode;

    /** 待填充的公共检查结果。 */
    private final TemplateInspection.Builder inspection;

    /** 已登记参数声明的片段，避免重复 include 造成重复键。 */
    private final Set<String> declaredFragments = new LinkedHashSet<>();

    /** 当前文档的静态样式和页面计划。 */
    private final CompiledDocumentPlan document;

    /** 创建一次文档检查收集过程。 */
    private XmlInspectionCollector(
            String templateCode, int contextVersion,
            Set<String> declaredOutputs, CompiledDocumentPlan document) {
        this.templateCode = templateCode;
        this.document = document;
        this.inspection = TemplateInspection.builder(
                templateCode, TemplateType.DOCUMENT, contextVersion);
        declaredOutputs.forEach(inspection::declaredOutput);
    }

    /**
     * 收集一个完整 XML 文档的静态契约。
     *
     * @param templateCode 文档模板代码
     * @param contextVersion 上下文契约版本
     * @param declaredOutputs 模板输出白名单
     * @param document 文档编译计划
     * @return 不可变检查结果
     */
    static TemplateInspection collect(
            String templateCode, int contextVersion,
            Set<String> declaredOutputs, CompiledDocumentPlan document) {
        return new XmlInspectionCollector(
                templateCode, contextVersion, declaredOutputs, document).collect();
    }

    /** 按页面和区域稳定顺序完成收集。 */
    private TemplateInspection collect() {
        if (document.pages().size() > 1) {
            inspection.feature(DocumentFeature.MULTIPLE_PAGE_SEQUENCES);
        }
        if (document.styleSheet().hasNamedStyles()) {
            inspection.feature(DocumentFeature.NAMED_STYLES);
        }
        Scope root = Scope.root();
        for (CompiledPagePlan page : document.pages()) {
            if (!page.header().isEmpty()) {
                inspection.feature(DocumentFeature.PAGE_HEADER);
            }
            if (!page.footer().isEmpty()) {
                inspection.feature(DocumentFeature.PAGE_FOOTER);
            }
            if (!page.pageNumbering().includedInCount()
                    || page.pageNumbering().restartAt().isPresent()) {
                inspection.feature(DocumentFeature.LOGICAL_PAGE_NUMBERING);
            }
            visit(page.header(), templateCode, root);
            visit(page.body(), templateCode, root);
            visit(page.footer(), templateCode, root);
        }
        return inspection.build();
    }

    /** 遍历同一词法作用域中的有序节点。 */
    private void visit(List<CompiledXmlNode> nodes, String currentTemplate, Scope scope) {
        for (CompiledXmlNode node : nodes) {
            visit(node, currentTemplate, scope);
        }
    }

    /** 收集一个编译节点及其所有可能后代。 */
    private void visit(CompiledXmlNode node, String currentTemplate, Scope scope) {
        addBuiltInNodeType(node);
        addStyleFeature(node);
        if ("field".equals(node.name())) {
            path(node.dataPath(), TemplatePathUsageKind.FIELD, node, currentTemplate, scope);
            String formatter = node.attributes().get("formatter");
            if (formatter != null) {
                inspection.formatter(formatter);
            }
        } else if ("image".equals(node.name()) && node.dataPath() != null) {
            path(node.dataPath(), TemplatePathUsageKind.IMAGE_RESOURCE,
                    node, currentTemplate, scope);
        } else if ("if".equals(node.name())) {
            if (node.condition() != null) {
                path(node.condition().path(), TemplatePathUsageKind.CONDITION,
                        node, currentTemplate, scope);
            } else {
                inspection.expressionLanguage(node.attributes().get("expression-language"));
                contribution(node.expressionPlan().inspectionContribution(),
                        TemplatePathUsageKind.EXPRESSION, node, currentTemplate, scope);
            }
        } else if ("for-each".equals(node.name())) {
            path(node.dataPath(), TemplatePathUsageKind.LOOP, node, currentTemplate, scope);
            Scope child = scope.loop(node.variableName());
            visit(node.children(), currentTemplate, child);
            return;
        } else if ("include".equals(node.name())) {
            visitInclude(node, currentTemplate, scope);
            return;
        } else if (node.tagPlan() != null) {
            inspection.customTag(node.name());
            contribution(node.tagPlan().inspectionContribution(),
                    TemplatePathUsageKind.CUSTOM_TAG, node, currentTemplate, scope);
        }
        visit(node.children(), currentTemplate, scope);
    }

    /** 收集 include 调用方参数，并在目标片段的闭合作用域继续遍历。 */
    private void visitInclude(
            CompiledXmlNode include, String currentTemplate, Scope callerScope) {
        CompiledXmlFragment fragment = include.includedFragment();
        Map<String, String> arguments = new LinkedHashMap<>();
        for (CompiledIncludeArgument argument : include.includeArguments()) {
            arguments.put(argument.name(), argument.dataPath().displayPath());
            path(argument.dataPath(), TemplatePathUsageKind.INCLUDE_ARGUMENT,
                    argument, currentTemplate, callerScope);
        }
        inspection.includeUsage(new TemplateIncludeUsage(
                currentTemplate, fragment.templateCode(), arguments,
                location(include, currentTemplate)));
        if (declaredFragments.add(fragment.templateCode())) {
            inspection.fragmentParameters(fragment.templateCode(), fragment.parameters());
        }
        visit(fragment.blocks(), fragment.templateCode(), Scope.fragment(fragment.parameters()));
    }

    /** 合并可信扩展声明的路径、节点类型和文档特性。 */
    private void contribution(
            TemplateInspectionContribution contribution, TemplatePathUsageKind kind,
            CompiledXmlNode node, String currentTemplate, Scope scope) {
        contribution.dataPaths().forEach(dataPath -> inspection.pathUsage(
                new TemplatePathUsage(dataPath, kind, scope.variables,
                        scope.parameters, location(node, currentTemplate))));
        contribution.nodeTypes().forEach(inspection::nodeType);
        contribution.features().forEach(inspection::feature);
    }

    /** 保存一个框架已经编译过的受限路径。 */
    private void path(
            CompiledDataPath path, TemplatePathUsageKind kind,
            CompiledXmlNode node, String currentTemplate, Scope scope) {
        inspection.pathUsage(new TemplatePathUsage(
                path.displayPath(), kind, scope.variables, scope.parameters,
                location(node, currentTemplate)));
    }

    /** 保存 include 参数自己的位置，多个 with 不再挤在父标签上。 */
    private void path(
            CompiledDataPath path, TemplatePathUsageKind kind,
            CompiledIncludeArgument argument, String currentTemplate, Scope scope) {
        inspection.pathUsage(new TemplatePathUsage(
                path.displayPath(), kind, scope.variables, scope.parameters,
                new TemplateSourceLocation(currentTemplate, argument.tagPath(),
                        argument.line(), argument.column())));
    }

    /** 根据 DSL 标签声明可能生成的核心文档节点类型。 */
    private void addBuiltInNodeType(CompiledXmlNode node) {
        Class<? extends DocumentNode> type = switch (node.name()) {
            case "section" -> SectionNode.class;
            case "heading" -> HeadingNode.class;
            case "paragraph" -> ParagraphNode.class;
            case "annotation" -> AnnotationNode.class;
            case "table" -> TableNode.class;
            case "image" -> ImageNode.class;
            case "page-break" -> PageBreakNode.class;
            case "table-of-contents" -> TableOfContentsNode.class;
            case "#text", "text", "field" -> TextNode.class;
            case "bookmark" -> BookmarkNode.class;
            case "link" -> InternalLinkNode.class;
            case "line-break" -> LineBreakNode.class;
            case "page-number" -> PageNumberNode.class;
            case "page-count" -> PageCountNode.class;
            default -> null;
        };
        if (type != null) {
            inspection.nodeType(type);
        }
    }

    /** 补充只有结合命名样式才能确定的输出特性。 */
    private void addStyleFeature(CompiledXmlNode node) {
        if ("paragraph".equals(node.name()) || "heading".equals(node.name())) {
            ParagraphStyle style = document.styleSheet().resolveParagraph(
                    node.attributes().get("style"));
            if (style.whitespaceMode() != WhitespaceMode.COLLAPSE
                    || style.textWrapMode() != TextWrapMode.NORMAL) {
                inspection.feature(DocumentFeature.TEXT_FLOW_CONTROL);
            }
        } else if ("table".equals(node.name())) {
            TableStyle style = document.styleSheet().resolveTable(
                    node.attributes().get("style"));
            if (style.repeatHeader()) {
                inspection.feature(DocumentFeature.REPEATED_TABLE_HEADER);
            }
            if (style.pageBreakPolicy() != TablePageBreakPolicy.AUTO) {
                inspection.feature(DocumentFeature.TABLE_PAGE_BREAK_POLICY);
            }
        }
    }

    /** 创建不包含源码正文的公共位置。 */
    private TemplateSourceLocation location(CompiledXmlNode node, String currentTemplate) {
        return new TemplateSourceLocation(
                currentTemplate, node.tagPath(), node.line(), node.column());
    }

    /**
     * inspection 遍历使用的变量和片段参数范围。
     *
     * @author leyland
     */
    private static final class Scope {

        /** 当前可见循环变量。 */
        private final Set<String> variables;

        /** 当前片段声明的参数。 */
        private final Set<String> parameters;

        /** 保存一份有序不可变作用域。 */
        private Scope(Set<String> variables, Set<String> parameters) {
            this.variables = Collections.unmodifiableSet(new LinkedHashSet<>(variables));
            this.parameters = Collections.unmodifiableSet(new LinkedHashSet<>(parameters));
        }

        /** @return 文档根作用域 */
        private static Scope root() {
            return new Scope(Set.of(), Set.of());
        }

        /** @return 只包含片段参数的闭合作用域 */
        private static Scope fragment(List<String> parameters) {
            return new Scope(Set.of(), new LinkedHashSet<>(parameters));
        }

        /** 在当前作用域增加一个循环变量。 */
        private Scope loop(String variable) {
            LinkedHashSet<String> nested = new LinkedHashSet<>(variables);
            nested.add(variable);
            return new Scope(nested, parameters);
        }
    }
}
