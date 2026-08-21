package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.Margins;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageNumbering;
import io.github.leylaragg.letool.print.document.PageOrientation;
import io.github.leylaragg.letool.print.document.PageSize;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspection;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;
import io.github.leylaragg.letool.print.xml.format.BuiltInPrintFormatters;
import io.github.leylaragg.letool.print.xml.format.FormatCompileContext;
import io.github.leylaragg.letool.print.xml.format.PrintFormatPlan;
import io.github.leylaragg.letool.print.xml.format.PrintFormatterRegistry;
import io.github.leylaragg.letool.print.xml.format.PrintValueFormatter;
import io.github.leylaragg.letool.print.xml.expression.ExpressionCompileContext;
import io.github.leylaragg.letool.print.xml.expression.PrintConditionExpression;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionPlan;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionRegistry;
import io.github.leylaragg.letool.print.xml.tag.PrintTagRegistry;
import io.github.leylaragg.letool.print.xml.tag.PrintTagHandler;
import io.github.leylaragg.letool.print.xml.tag.PrintTagPlan;
import io.github.leylaragg.letool.print.xml.tag.TagCompileContext;
import io.github.leylaragg.letool.print.xml.tag.TagContentModel;
import io.github.leylaragg.letool.print.xml.tag.TagPlacement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将 Letool 受控 XML 模板编译为不透明快照的入口。
 *
 * @author leyland
 */
public final class XmlTemplateCompiler {

    /** 循环变量名的稳定安全格式。 */
    private static final Pattern LOOP_VARIABLE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    /** 表达式 inspection 允许属性链中带受控的非负数组下标。 */
    private static final Pattern EXPRESSION_INSPECTION_PATH = Pattern.compile(
            "\\$?[A-Za-z][A-Za-z0-9_-]*(?:\\[[0-9]+])?"
                    + "(?:\\.[A-Za-z][A-Za-z0-9_-]*(?:\\[[0-9]+])?)*");

    /** 编译阶段使用的不可变格式化器注册表。 */
    private final PrintFormatterRegistry formatterRegistry;

    /** 编译阶段使用的不可变表达式注册表。 */
    private final PrintExpressionRegistry expressionRegistry;

    /** 编译阶段使用的不可变自定义标签注册表。 */
    private final PrintTagRegistry tagRegistry;

    /** 统一执行安全解码、StAX 治理和 XML 语法检查的源码解析器。 */
    private final XmlSourceParser sourceParser;

    /** 使用内置格式化器创建编译器。 */
    public XmlTemplateCompiler() {
        this(BuiltInPrintFormatters.registry(),
                new PrintExpressionRegistry(List.of()), new PrintTagRegistry(List.of()));
    }

    /**
     * 使用显式格式化器注册表创建编译器。
     *
     * @param formatterRegistry 编译阶段使用的不可变注册表
     */
    public XmlTemplateCompiler(PrintFormatterRegistry formatterRegistry) {
        this(formatterRegistry,
                new PrintExpressionRegistry(List.of()), new PrintTagRegistry(List.of()));
    }

    /**
     * 使用显式格式化器、表达式和自定义标签注册表创建编译器。
     *
     * @param formatterRegistry 格式化器注册表
     * @param expressionRegistry 条件表达式注册表
     * @param tagRegistry 自定义标签注册表
     */
    public XmlTemplateCompiler(
            PrintFormatterRegistry formatterRegistry,
            PrintExpressionRegistry expressionRegistry,
            PrintTagRegistry tagRegistry) {
        this.formatterRegistry = Objects.requireNonNull(
                formatterRegistry, "formatterRegistry 不能为空");
        this.expressionRegistry = Objects.requireNonNull(
                expressionRegistry, "expressionRegistry 不能为空");
        this.tagRegistry = Objects.requireNonNull(tagRegistry, "tagRegistry 不能为空");
        this.sourceParser = new XmlSourceParser(new XmlGrammar(this.tagRegistry));
    }

    /**
     * 编译模板快照。
     *
     * @param template Letool XML 模板
     * @return 完成安全和结构校验的编译快照
     * @throws NullPointerException 模板为空时抛出
     */
    public CompiledXmlTemplate compile(PrintTemplate template) {
        Objects.requireNonNull(template, "template 不能为空");
        ParsedXmlTemplate parsed = parse(new TemplateDefinition(TemplateType.DOCUMENT, template));
        return compileDocument(parsed, compileSourceTree(parsed.root()));
    }

    /** 将安全解析树转换为尚未附加动态计划的编译树。 */
    private CompiledXmlNode compileSourceTree(ParsedXmlNode node) {
        List<CompiledXmlNode> children = new ArrayList<>(node.children().size());
        for (ParsedXmlNode child : node.children()) {
            children.add(compileSourceTree(child));
        }
        return new CompiledXmlNode(node.name(), node.attributes(), children,
                node.text(), node.line(), node.column());
    }

    /** 查找已经显式注册的自定义标签，内置标签始终返回空。 */
    private PrintTagHandler customTag(String name) {
        return tagRegistry.tagNames().contains(name) ? tagRegistry.require(name) : null;
    }

    /**
     * 解析集合中的 XML 定义，按模板用途校验根结构并保留尚未解析的 include。
     *
     * @param definition 模板定义
     * @return 完成安全和结构校验的解析快照
     */
    ParsedXmlTemplate parse(TemplateDefinition definition) {
        Objects.requireNonNull(definition, "definition 不能为空");
        PrintTemplate template = definition.template();
        if (!TemplateFormat.LETOOL_XML.equals(template.templateFormat())) {
            throw PrintCompilationException.invalid(template.templateCode() + "：模板格式不是 letool-xml");
        }
        if (template.dslVersion() != XmlDsl.VERSION) {
            throw PrintCompilationException.invalid(template.templateCode() + "：不支持的 DSL 版本");
        }
        ParsedXmlTemplate parsed = sourceParser.parse(definition);
        if (definition.type() == TemplateType.DOCUMENT) {
            validateDocument(template, parsed.root());
        } else {
            validateFragment(template, parsed.root());
        }
        return parsed;
    }

    /**
     * 将已经解析引用的文档编译为可绑定快照。
     *
     * @param parsed 文档解析结果
     * @param resolvedRoot 已解析 include 的文档根节点
     * @return 可并发绑定的文档快照
     */
    CompiledXmlTemplate compileDocument(ParsedXmlTemplate parsed, CompiledXmlNode resolvedRoot) {
        PrintTemplate template = parsed.template();
        CompiledDocumentPlan documentPlan = compileDocumentPlan(template.templateCode(), resolvedRoot);
        Set<String> outputs = declaredOutputs(template.templateCode(), resolvedRoot);
        TemplateInspection inspection = XmlInspectionCollector.collect(
                template.templateCode(), template.contextVersion(), outputs, documentPlan);
        return new CompiledXmlTemplate(template.templateCode(), template.dslVersion(),
                template.templateSetVersion(), template.contextVersion(),
                resolvedRoot, documentPlan, inspection);
    }

    /** 解析文档根节点可选的输出格式白名单。 */
    private Set<String> declaredOutputs(
            String templateCode, CompiledXmlNode document) {
        String value = document.attributes().get("outputs");
        if (value == null) {
            return Set.of();
        }
        String[] parts = value.split(",", -1);
        if (parts.length > 16) {
            throw nodeLocated(templateCode, document, "outputs 数量不能超过 16");
        }
        LinkedHashSet<String> outputs = new LinkedHashSet<>();
        for (String part : parts) {
            String output = part.trim();
            if (!output.matches("[a-z][a-z0-9._-]{0,63}")) {
                throw nodeLocated(templateCode, document, "outputs 包含非法格式标识");
            }
            if (!outputs.add(output)) {
                throw nodeLocated(templateCode, document, "outputs 不能重复");
            }
        }
        return Collections.unmodifiableSet(outputs);
    }

    /**
     * 将已经解析引用的片段编译为闭合作用域的块节点。
     *
     * @param parsed 片段解析结果
     * @param resolvedRoot 已解析 include 的片段根节点
     * @return 可以被多个文档复用的编译片段
     */
    CompiledXmlFragment compileFragment(ParsedXmlTemplate parsed, CompiledXmlNode resolvedRoot) {
        String templateCode = parsed.template().templateCode();
        List<String> parameters = fragmentParameters(templateCode, resolvedRoot);
        Set<String> parameterScope = new LinkedHashSet<>(parameters);
        List<CompiledXmlNode> blocks = new ArrayList<>(resolvedRoot.children().size());
        for (CompiledXmlNode child : resolvedRoot.children()) {
            blocks.add(compileDynamicTree(
                    templateCode, child, "/fragment", parameterScope, BindingDomain.BLOCKS));
        }
        return new CompiledXmlFragment(templateCode, parameters, blocks);
    }

    /** 解析并去重片段根节点声明的有序参数。 */
    private List<String> fragmentParameters(
            String templateCode, CompiledXmlNode fragment) {
        String value = fragment.attributes().get("parameters");
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split(",", -1);
        if (parts.length > 64) {
            throw nodeLocated(templateCode, fragment, "片段参数数量不能超过 64");
        }
        LinkedHashSet<String> parameters = new LinkedHashSet<>();
        for (String part : parts) {
            String parameter = part.trim();
            if (!LOOP_VARIABLE.matcher(parameter).matches()) {
                throw nodeLocated(templateCode, fragment, "片段参数名不合法");
            }
            if (!parameters.add(parameter)) {
                throw nodeLocated(templateCode, fragment, "片段参数名不能重复");
            }
        }
        return List.copyOf(parameters);
    }

    /** 校验根结构和 XML 声明版本。 */
    private void validateDocument(PrintTemplate template, ParsedXmlNode root) {
        if (!"document".equals(root.name())) {
            throw nodeLocated(template.templateCode(), root, "根标签必须为 document");
        }
        boolean pageSeen = false;
        int stylesCount = 0;
        int pageCount = 0;
        for (ParsedXmlNode child : root.children()) {
            if ("styles".equals(child.name())) {
                stylesCount++;
                if (pageSeen || stylesCount > 1) {
                    throw nodeLocated(template.templateCode(), child,
                            "styles 必须唯一并位于所有 page 之前");
                }
            } else if ("page".equals(child.name())) {
                pageSeen = true;
                pageCount++;
            }
        }
        if (pageCount < 1) {
            throw nodeLocated(template.templateCode(), root, "document 至少包含一个 page");
        }
        String declaredVersion = root.attributes().get("context-version");
        if (declaredVersion == null || !declaredVersion.equals(Integer.toString(template.contextVersion()))) {
            throw nodeLocated(template.templateCode(), root, "context-version 与模板快照不一致");
        }
    }

    /** 将文档静态配置和每个页面区域编译为绑定计划。 */
    private CompiledDocumentPlan compileDocumentPlan(
            String templateCode, CompiledXmlNode document) {
        CompiledXmlNode stylesNode = document.children().stream()
                .filter(child -> "styles".equals(child.name()))
                .findFirst().orElse(null);
        StyleSheet styleSheet = XmlStyleCompiler.compile(templateCode, stylesNode);
        List<CompiledPagePlan> pages = new ArrayList<>();
        int pageIndex = 0;
        for (CompiledXmlNode child : document.children()) {
            if (!"page".equals(child.name())) {
                continue;
            }
            pages.add(compilePage(templateCode, child, pageIndex++, styleSheet));
        }
        try {
            return new CompiledDocumentPlan(
                    new DocumentMetadata(
                            document.attributes().get("title"),
                            document.attributes().get("author"),
                            document.attributes().get("language")),
                    styleSheet,
                    pages);
        } catch (RuntimeException exception) {
            throw nodeLocated(templateCode, document, "文档静态配置无效", exception);
        }
    }

    /** 编译单个页面序列，并在绑定前校验区域节点的样式引用。 */
    private CompiledPagePlan compilePage(
            String templateCode, CompiledXmlNode page, int pageIndex, StyleSheet styleSheet) {
        String pagePath = "/document/page[" + (pageIndex + 1) + "]";
        List<CompiledXmlNode> header = List.of();
        List<CompiledXmlNode> body = List.of();
        List<CompiledXmlNode> footer = List.of();
        for (CompiledXmlNode region : page.children()) {
            List<CompiledXmlNode> compiled = compileRegion(
                    templateCode, region, pagePath + "/" + region.name());
            if ("page-header".equals(region.name())) {
                header = compiled;
            } else if ("page-body".equals(region.name())) {
                body = compiled;
            } else {
                footer = compiled;
            }
        }
        validateStyleReferences(templateCode, styleSheet, header);
        validateStyleReferences(templateCode, styleSheet, body);
        validateStyleReferences(templateCode, styleSheet, footer);
        PageNumbering numbering = pageNumbering(templateCode, page);
        if (!numbering.includedInCount()
                && (containsNode(header, "page-number")
                || containsNode(body, "page-number")
                || containsNode(footer, "page-number"))) {
            throw nodeLocated(templateCode, page,
                    "excluded 页面序列不能包含 page-number");
        }
        return new CompiledPagePlan(
                pageLayout(templateCode, page), numbering, header, body, footer);
    }

    /** 编译一个页面区域中的块级节点。 */
    private List<CompiledXmlNode> compileRegion(
            String templateCode, CompiledXmlNode region, String parentPath) {
        List<CompiledXmlNode> compiled = new ArrayList<>(region.children().size());
        for (CompiledXmlNode child : region.children()) {
            compiled.add(compileDynamicTree(
                    templateCode, child, parentPath, Set.of(), BindingDomain.BLOCKS));
        }
        return List.copyOf(compiled);
    }

    /** 把 page 的受控属性转换为不可变页面布局。 */
    private PageLayout pageLayout(String templateCode, CompiledXmlNode page) {
        try {
            PageSize size = switch (page.attributes()
                    .getOrDefault("size", "A4").toUpperCase(Locale.ROOT)) {
                case "A4" -> PageSize.A4;
                case "LETTER" -> PageSize.LETTER;
                default -> throw nodeLocated(templateCode, page, "页面尺寸不受支持");
            };
            PageOrientation orientation = switch (page.attributes()
                    .getOrDefault("orientation", "portrait").toLowerCase(Locale.ROOT)) {
                case "portrait" -> PageOrientation.PORTRAIT;
                case "landscape" -> PageOrientation.LANDSCAPE;
                default -> throw nodeLocated(templateCode, page, "页面方向不受支持");
            };
            Map<String, String> attributes = page.attributes();
            boolean uniform = attributes.containsKey("margin");
            boolean anySide = Set.of("margin-top", "margin-right", "margin-bottom", "margin-left")
                    .stream().anyMatch(attributes::containsKey);
            if (uniform && anySide) {
                throw nodeLocated(templateCode, page, "统一边距不能与单边边距混用");
            }
            Margins margins;
            if (anySide) {
                if (!Set.of("margin-top", "margin-right", "margin-bottom", "margin-left")
                        .stream().allMatch(attributes::containsKey)) {
                    throw nodeLocated(templateCode, page, "四个单边边距必须同时声明");
                }
                margins = new Margins(
                        XmlValueParser.micrometers(attributes.get("margin-top"),
                                templateCode, page, "margin-top"),
                        XmlValueParser.micrometers(attributes.get("margin-right"),
                                templateCode, page, "margin-right"),
                        XmlValueParser.micrometers(attributes.get("margin-bottom"),
                                templateCode, page, "margin-bottom"),
                        XmlValueParser.micrometers(attributes.get("margin-left"),
                                templateCode, page, "margin-left"));
            } else {
                int margin = XmlValueParser.micrometers(
                        attributes.getOrDefault("margin", "20mm"),
                        templateCode, page, "margin");
                margins = new Margins(margin, margin, margin, margin);
            }
            return new PageLayout(size, orientation, margins);
        } catch (PrintCompilationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw nodeLocated(templateCode, page, "页面布局无效", exception);
        }
    }

    /** 把页面页码属性转换为核心规则。 */
    private PageNumbering pageNumbering(String templateCode, CompiledXmlNode page) {
        String mode = page.attributes().getOrDefault("numbering", "continue");
        String start = page.attributes().get("start-page-number");
        try {
            return switch (mode) {
                case "continue" -> {
                    if (start != null) {
                        throw nodeLocated(templateCode, page,
                                "continue 不能声明 start-page-number");
                    }
                    yield PageNumbering.counted();
                }
                case "restart" -> {
                    if (start == null) {
                        throw nodeLocated(templateCode, page,
                                "restart 必须声明 start-page-number");
                    }
                    yield PageNumbering.countedFrom(XmlValueParser.positiveInteger(
                            start, templateCode, page, "start-page-number"));
                }
                case "excluded" -> {
                    if (start != null) {
                        throw nodeLocated(templateCode, page,
                                "excluded 不能声明 start-page-number");
                    }
                    yield PageNumbering.excluded();
                }
                default -> throw nodeLocated(templateCode, page, "numbering 不受支持");
            };
        } catch (PrintCompilationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw nodeLocated(templateCode, page, "页面页码规则无效", exception);
        }
    }

    /** 根据节点语义在编译期校验类型明确的命名样式引用。 */
    private void validateStyleReferences(
            String templateCode, StyleSheet styleSheet, List<CompiledXmlNode> nodes) {
        for (CompiledXmlNode node : nodes) {
            String style = node.attributes().get("style");
            try {
                switch (node.name()) {
                    case "paragraph", "heading" -> styleSheet.resolveParagraph(style);
                    case "text", "field", "page-number", "page-count" ->
                            styleSheet.resolveText(style);
                    case "table" -> styleSheet.resolveTable(style);
                    case "cell" -> styleSheet.resolveCell(style);
                    default -> {
                        // 其他节点没有可引用的命名样式。
                    }
                }
            } catch (RuntimeException exception) {
                throw nodeLocated(templateCode, node, "样式引用不存在或类型不匹配", exception);
            }
            validateStyleReferences(templateCode, styleSheet, node.children());
            if (node.includedFragment() != null) {
                validateStyleReferences(
                        node.includedFragment().templateCode(), styleSheet,
                        node.includedFragment().blocks());
            }
        }
    }

    /** 查找页面区域及其片段中是否可能生成指定节点。 */
    private boolean containsNode(List<CompiledXmlNode> nodes, String name) {
        for (CompiledXmlNode node : nodes) {
            if (name.equals(node.name()) || containsNode(node.children(), name)) {
                return true;
            }
            if (node.includedFragment() != null
                    && containsNode(node.includedFragment().blocks(), name)) {
                return true;
            }
        }
        return false;
    }

    /** 把动态属性编译为不可执行的包内描述。 */
    private CompiledXmlNode compileDynamicTree(
            String templateCode,
            CompiledXmlNode node,
            String parentPath,
            Set<String> variables,
            BindingDomain domain) {
        if (domain == BindingDomain.TABLE_ROWS
                && !Set.of("row", "if", "for-each", "then", "else")
                .contains(node.name())) {
            throw nodeLocated(templateCode, node, "表格动态结构只能产生 row");
        }
        if (domain == BindingDomain.BLOCKS && "row".equals(node.name())) {
            throw nodeLocated(templateCode, node, "row 只能出现在表格 header 或 body 中");
        }
        if ("include".equals(node.name())) {
            if (node.includedFragment() == null) {
                throw nodeLocated(templateCode, node, "include 必须通过模板集合编译器解析");
            }
            String tagPath = parentPath + "/include";
            List<CompiledIncludeArgument> arguments = compileIncludeArguments(
                    templateCode, node, tagPath, variables);
            CompiledXmlNode include = new CompiledXmlNode(
                    node.name(), node.attributes(), List.of(), "", node.line(), node.column(),
                    tagPath, null, null, null, null, null, null,
                    node.includedFragment());
            return include.withIncludeArguments(arguments);
        }
        String tagPath = "#text".equals(node.name()) ? parentPath : parentPath + "/" + node.name();
        CompiledDataPath dataPath = null;
        CompiledCondition condition = null;
        String variableName = null;
        PrintFormatPlan formatPlan = null;
        PrintExpressionPlan expressionPlan = null;
        CompiledTagPlan tagPlan = null;
        Set<String> childVariables = variables;
        if ("for-each".equals(node.name())) {
            variableName = node.attributes().get("var");
            if (variableName == null || !LOOP_VARIABLE.matcher(variableName).matches()) {
                throw nodeLocated(templateCode, node, "循环变量名不合法");
            }
            if (variables.contains(variableName)) {
                throw nodeLocated(templateCode, node, "循环变量不能与外层变量重名");
            }
            dataPath = CompiledDataPath.compile(
                    node.attributes().get("items"), variables, templateCode,
                    tagPath, node.line(), node.column());
            LinkedHashSet<String> nested = new LinkedHashSet<>(variables);
            nested.add(variableName);
            childVariables = Collections.unmodifiableSet(nested);
        }
        PrintTagHandler customTag = customTag(node.name());
        if (customTag != null) {
            if (domain == BindingDomain.TABLE_ROWS) {
                throw nodeLocated(templateCode, node, "表格动态结构只能产生 row");
            }
            try {
                PrintTagPlan plan = customTag.compile(new TagCompileContext(
                        node.name(), node.attributes(), tagPath + "，第 " + node.line()
                                + " 行，第 " + node.column() + " 列"));
                if (plan == null) {
                    throw new IllegalStateException("null plan");
                }
                TemplateInspectionContribution contribution = plan.inspectionContribution();
                if (contribution == null || contribution.nodeTypes().isEmpty()) {
                    throw new IllegalStateException("missing inspection contribution");
                }
                validateInspectionPaths(
                        contribution, variables, templateCode, node, tagPath);
                tagPlan = new CompiledTagPlan(
                        customTag.placement(), customTag.contentModel(), plan,
                        contribution, variables.isEmpty());
            } catch (RuntimeException exception) {
                throw nodeLocated(templateCode, node, "自定义标签编译失败", exception);
            }
        }
        List<CompiledXmlNode> children = new ArrayList<>(node.children().size());
        BindingDomain childDomain = childBindingDomain(node.name(), domain);
        for (CompiledXmlNode child : node.children()) {
            children.add(compileDynamicTree(
                    templateCode, child, tagPath, childVariables, childDomain));
        }
        if ("field".equals(node.name())) {
            dataPath = CompiledDataPath.compile(
                    node.attributes().get("path"), variables, templateCode,
                    tagPath, node.line(), node.column());
            formatPlan = compileFormatPlan(templateCode, node, tagPath, children);
        }
        if ("image".equals(node.name()) && node.attributes().containsKey("resource-path")) {
            dataPath = CompiledDataPath.compile(
                    node.attributes().get("resource-path"), variables, templateCode,
                    tagPath, node.line(), node.column());
        }
        if ("if".equals(node.name())) {
            if (node.attributes().containsKey("expression-language")
                    || node.attributes().containsKey("test")) {
                expressionPlan = compileExpressionPlan(
                        templateCode, node, tagPath, variables);
            } else {
                condition = CompiledCondition.compile(
                        node.attributes(), variables, templateCode,
                        tagPath, node.line(), node.column());
            }
        }
        if ("for-each".equals(node.name())) {
            if (children.isEmpty()) {
                throw nodeLocated(templateCode, node, "for-each 至少包含一个块节点");
            }
            rejectLoopIds(templateCode, node, children);
        }
        return new CompiledXmlNode(
                node.name(), node.attributes(), children, node.text(), node.line(), node.column(),
                tagPath, dataPath, condition, variableName, formatPlan, expressionPlan, tagPlan);
    }

    /** 将显式条件表达式编译为不持有 XML 对象的计划。 */
    private PrintExpressionPlan compileExpressionPlan(
            String templateCode, CompiledXmlNode node, String tagPath, Set<String> variables) {
        Map<String, String> attributes = node.attributes();
        String language = attributes.get("expression-language");
        String test = attributes.get("test");
        if (language == null || test == null) {
            throw nodeLocated(templateCode, node,
                    "expression-language 和 test 必须同时声明");
        }
        if (attributes.keySet().stream().anyMatch(
                name -> Set.of("path", "operator", "value", "value-type").contains(name))) {
            throw nodeLocated(templateCode, node, "扩展表达式不能与结构化条件属性混用");
        }
        if (test.isBlank() || test.length() > XmlDsl.MAX_EXPRESSION_CHARACTERS) {
            throw nodeLocated(templateCode, node, "条件表达式不能为空白且不能超过长度限制");
        }
        PrintConditionExpression expression;
        try {
            expression = expressionRegistry.require(language);
        } catch (RuntimeException exception) {
            throw nodeLocated(templateCode, node, "条件表达式语言未注册");
        }
        try {
            PrintExpressionPlan plan = expression.compile(new ExpressionCompileContext(
                    language, test, tagPath + "，第 " + node.line()
                            + " 行，第 " + node.column() + " 列"));
            if (plan == null) {
                throw new IllegalStateException("null plan");
            }
            TemplateInspectionContribution contribution = plan.inspectionContribution();
            if (contribution == null) {
                throw new IllegalStateException("missing inspection contribution");
            }
            TemplateInspectionContribution frozenContribution =
                    normalizeExpressionInspectionContribution(
                            contribution, variables, templateCode, node, tagPath);
            return PrintExpressionPlan.of(frozenContribution, plan::evaluate);
        } catch (RuntimeException exception) {
            throw nodeLocated(templateCode, node, "表达式提供方编译失败", exception);
        }
    }

    /** 扩展声明的路径也要经过 XML 前端的词法作用域和安全语法校验。 */
    private void validateInspectionPaths(
            TemplateInspectionContribution contribution,
            Set<String> variables, String templateCode,
            CompiledXmlNode node, String tagPath) {
        for (String dataPath : contribution.dataPaths()) {
            CompiledDataPath.compile(
                    dataPath, variables, templateCode, tagPath, node.line(), node.column());
        }
    }

    /**
     * 校验表达式读取链，并将词法变量统一为公共 XML 路径形式。
     *
     * @param contribution 表达式提供方的原始静态贡献
     * @param variables 当前可见变量和片段参数
     * @param templateCode 当前模板代码
     * @param node 当前条件节点
     * @param tagPath 当前安全标签路径
     * @return 可以冻结进编译快照的静态贡献
     */
    private TemplateInspectionContribution normalizeExpressionInspectionContribution(
            TemplateInspectionContribution contribution,
            Set<String> variables, String templateCode,
            CompiledXmlNode node, String tagPath) {
        TemplateInspectionContribution.Builder normalized =
                TemplateInspectionContribution.builder();
        for (String dataPath : contribution.dataPaths()) {
            if (dataPath.length() > XmlDsl.MAX_PATH_CHARACTERS
                    || !EXPRESSION_INSPECTION_PATH.matcher(dataPath).matches()) {
                throw nodeLocated(templateCode, node, "表达式声明了非法数据路径");
            }
            String rootName = expressionPathRoot(dataPath);
            if (dataPath.startsWith("$") && !variables.contains(rootName)) {
                throw nodeLocated(templateCode, node, "表达式声明了未定义变量");
            }
            normalized.dataPath(!dataPath.startsWith("$") && variables.contains(rootName)
                    ? "$" + dataPath : dataPath);
        }
        contribution.nodeTypes().forEach(normalized::nodeType);
        contribution.features().forEach(normalized::feature);
        return normalized.build();
    }

    /**
     * 取得表达式读取链的首段名称，不保留变量前缀。
     *
     * @param dataPath 已通过表达式路径语法校验的读取链
     * @return 根属性、循环变量或片段参数名
     */
    private String expressionPathRoot(String dataPath) {
        int start = dataPath.startsWith("$") ? 1 : 0;
        int dot = dataPath.indexOf('.', start);
        int bracket = dataPath.indexOf('[', start);
        int end = dot < 0 ? dataPath.length() : dot;
        if (bracket >= 0 && bracket < end) {
            end = bracket;
        }
        return dataPath.substring(start, end);
    }

    /** 校验片段根结构，片段只能提供非空块节点。 */
    private void validateFragment(PrintTemplate template, ParsedXmlNode root) {
        if (!"fragment".equals(root.name())) {
            throw nodeLocated(template.templateCode(), root, "根标签必须为 fragment");
        }
        if (root.children().isEmpty()) {
            throw nodeLocated(template.templateCode(), root, "fragment 至少包含一个块节点");
        }
    }

    /** 确定子节点应遵循普通块还是表格行结果域。 */
    private BindingDomain childBindingDomain(String nodeName, BindingDomain current) {
        if ("header".equals(nodeName) || "body".equals(nodeName)) {
            return BindingDomain.TABLE_ROWS;
        }
        if ("if".equals(nodeName) || "then".equals(nodeName)
                || "else".equals(nodeName) || "for-each".equals(nodeName)) {
            return current;
        }
        return BindingDomain.BLOCKS;
    }

    /** 将 field 的静态选项编译为可直接绑定的格式化计划。 */
    private PrintFormatPlan compileFormatPlan(
            String templateCode, CompiledXmlNode field, String tagPath,
            List<CompiledXmlNode> children) {
        String formatterName = field.attributes().get("formatter");
        if (formatterName == null) {
            if (!children.isEmpty()) {
                throw nodeLocated(templateCode, field, "format-option 必须与 formatter 一起使用");
            }
            return null;
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (CompiledXmlNode option : children) {
            String name = option.attributes().get("name");
            if (options.putIfAbsent(name, option.attributes().get("value")) != null) {
                throw nodeLocated(templateCode, option, "format-option.name 不能重复");
            }
        }
        PrintValueFormatter formatter;
        try {
            formatter = formatterRegistry.require(formatterName);
        } catch (IllegalArgumentException exception) {
            throw nodeLocated(templateCode, field, "格式化器不存在或未注册");
        }
        try {
            PrintFormatPlan plan = formatter.compile(
                    Map.copyOf(options),
                    new FormatCompileContext(
                            templateCode, tagPath, field.line(), field.column()));
            if (plan == null) {
                throw new IllegalArgumentException("格式化器返回了空计划");
            }
            return plan;
        } catch (RuntimeException exception) {
            throw nodeLocated(templateCode, field, "格式化器配置无效", exception);
        }
    }

    /** 禁止循环展开后产生重复静态 ID。 */
    private void rejectLoopIds(
            String templateCode, CompiledXmlNode loop, List<CompiledXmlNode> children) {
        for (CompiledXmlNode child : children) {
            if (child.attributes().containsKey("id")) {
                throw nodeLocated(templateCode, loop, "for-each 后代不能声明静态 ID");
            }
            rejectLoopIds(templateCode, loop, child.children());
        }
    }

    /** 创建包含编译节点起始位置的异常。 */
    private PrintCompilationException nodeLocated(
            String templateCode,
            CompiledXmlNode node,
            String detail) {
        return PrintCompilationException.invalid(
                templateCode + "：第 " + node.line() + " 行，第 " + node.column() + " 列：" + detail);
    }

    /** 编译 include 的调用方路径，并与目标片段参数逐项对齐。 */
    private List<CompiledIncludeArgument> compileIncludeArguments(
            String templateCode, CompiledXmlNode include, String tagPath,
            Set<String> variables) {
        Map<String, CompiledIncludeArgument> declared = new LinkedHashMap<>();
        for (CompiledXmlNode child : include.children()) {
            String name = child.attributes().get("name");
            CompiledDataPath path = CompiledDataPath.compile(
                    child.attributes().get("path"), variables, templateCode,
                    tagPath + "/with", child.line(), child.column());
            CompiledIncludeArgument argument = new CompiledIncludeArgument(
                    name, path, tagPath + "/with", child.line(), child.column());
            if (declared.putIfAbsent(name, argument) != null) {
                throw nodeLocated(templateCode, child, "include 参数名不能重复");
            }
        }
        List<String> expected = include.includedFragment().parameters();
        if (!declared.keySet().equals(new LinkedHashSet<>(expected))) {
            throw nodeLocated(templateCode, include, "include 参数必须与片段声明完整一致");
        }
        List<CompiledIncludeArgument> ordered = new ArrayList<>(expected.size());
        for (String name : expected) {
            ordered.add(declared.get(name));
        }
        return List.copyOf(ordered);
    }

    /** 创建带原因链但不暴露第三方消息的节点编译异常。 */
    private PrintCompilationException nodeLocated(
            String templateCode, CompiledXmlNode node, String detail, Throwable cause) {
        return PrintCompilationException.invalid(
                templateCode + "：第 " + node.line() + " 行，第 " + node.column()
                        + " 列：" + detail,
                cause);
    }

    /** 为解析树节点创建不包含模板正文的定位异常。 */
    private PrintCompilationException nodeLocated(
            String templateCode, ParsedXmlNode node, String detail) {
        return PrintCompilationException.invalid(
                templateCode + "：第 " + node.line() + " 行，第 " + node.column()
                        + " 列：" + detail);
    }

    /** 动态结构在当前位置允许生成的节点类型。 */
    private enum BindingDomain {
        BLOCKS,
        TABLE_ROWS
    }
}
