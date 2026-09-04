package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageRegion;
import io.github.leylaragg.letool.print.document.PageSequence;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.HeadingNode;
import io.github.leylaragg.letool.print.document.node.ImageNode;
import io.github.leylaragg.letool.print.document.node.InlineNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.LineBreakNode;
import io.github.leylaragg.letool.print.document.node.PageCountNode;
import io.github.leylaragg.letool.print.document.node.PageBreakNode;
import io.github.leylaragg.letool.print.document.node.PageNumberNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TableOfContentsNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.xml.expression.ExpressionEvaluationContext;
import io.github.leylaragg.letool.print.xml.tag.TagBindingContext;
import io.github.leylaragg.letool.print.xml.tag.TagContentModel;
import io.github.leylaragg.letool.print.xml.tag.TagPlacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将安全 XML 编译快照与只读上下文绑定为通用文档模型。
 *
 * <p>支持基础标签、受限动态结构、片段引用、格式计划和可信扩展计划。</p>
 *
 * @author leyland
 */
public final class XmlTemplateBinder {

    /**
     * 绑定编译模板和只读上下文。
     *
     * @param template 完成安全编译的 XML 模板
     * @param context 版本匹配的只读上下文
     * @return 完成结构校验的通用文档模型
     */
    public DocumentModel bind(CompiledXmlTemplate template, PrintContext context) {
        if (template == null || context == null) {
            throw PrintValidationException.invalidRequest("编译模板和上下文不能为空");
        }
        if (template.contextVersion() != context.version()) {
            throw PrintValidationException.invalidRequest("编译模板与上下文版本不一致");
        }
        CompiledDocumentPlan document = template.documentPlan();
        BindingGovernor governor = new BindingGovernor(template.templateCode());
        BindingScope scope = new BindingScope(context.root());
        List<PageSequence> sequences = new ArrayList<>(document.pages().size());
        for (CompiledPagePlan page : document.pages()) {
            List<BlockNode> header = bindBlocks(
                    page.header(), scope, template.templateCode(), governor);
            List<BlockNode> body = bindBlocks(
                    page.body(), scope, template.templateCode(), governor);
            List<BlockNode> footer = bindBlocks(
                    page.footer(), scope, template.templateCode(), governor);
            sequences.add(new PageSequence(
                    page.pageLayout(), new PageRegion(header), new PageRegion(footer),
                    page.pageNumbering(), body));
        }
        try {
            return new DocumentModel(document.metadata(), document.styleSheet(), sequences);
        } catch (PrintValidationException exception) {
            if (governor.customTagUsed()) {
                throw PrintValidationException.invalidDocument(
                        template.templateCode() + "：自定义标签返回的文档模型校验失败",
                        exception);
            }
            throw exception;
        }
    }

    /** 绑定有序块级节点列表。 */
    private List<BlockNode> bindBlocks(
            List<CompiledXmlNode> nodes, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<BlockNode> blocks = new ArrayList<>(nodes.size());
        for (CompiledXmlNode node : nodes) {
            if ("include".equals(node.name())) {
                CompiledXmlFragment fragment = node.includedFragment();
                if (fragment == null) {
                    throw bindingError(templateCode, node, "include 尚未解析");
                }
                Map<String, JsonNode> parameters = includeParameters(
                        node, scope, templateCode, governor);
                blocks.addAll(bindBlocks(
                        fragment.blocks(), scope.fragment(parameters),
                        fragment.templateCode(), governor));
            } else if ("if".equals(node.name())) {
                blocks.addAll(bindCondition(
                        node, scope, templateCode, governor, this::bindBlocks));
            } else if ("for-each".equals(node.name())) {
                blocks.addAll(bindLoop(
                        node, scope, templateCode, governor, this::bindBlocks));
            } else {
                blocks.addAll(bindBlock(node, scope, templateCode, governor));
            }
        }
        return List.copyOf(blocks);
    }

    /** 在调用方作用域解析全部参数，再一次性建立闭合片段作用域。 */
    private Map<String, JsonNode> includeParameters(
            CompiledXmlNode include, BindingScope caller, String templateCode,
            BindingGovernor governor) {
        governor.addDynamicOperations(include.includeArguments().size());
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        for (CompiledIncludeArgument argument : include.includeArguments()) {
            BindingScope.ResolvedValue resolved = caller.resolve(argument.dataPath());
            if (resolved.isInvalid()) {
                throw bindingError(templateCode, include,
                        "include 参数路径无法继续遍历：" + argument.dataPath().displayPath());
            }
            if (!resolved.isPresent()) {
                throw bindingError(templateCode, include,
                        "include 参数路径不存在：" + argument.dataPath().displayPath());
            }
            parameters.put(argument.name(), resolved.value());
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /** 绑定一个静态块级标签。 */
    private List<BlockNode> bindBlock(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        if (node.tagPlan() != null) {
            return List.of(bindCustomBlock(node, scope, templateCode, governor));
        }
        if ("section".equals(node.name())) {
            List<BlockNode> children = bindBlocks(
                    node.children(), scope, templateCode, governor);
            if (children.isEmpty()) {
                return List.of();
            }
            governor.addNodes(1);
            return List.of(new SectionNode(
                    node.attributes().getOrDefault("id", ""), children));
        }
        if ("table".equals(node.name())) {
            return bindTable(node, scope, templateCode, governor);
        }
        if ("image".equals(node.name())) {
            governor.addNodes(1);
            governor.addText(node.attributes().get("alt").length());
            return List.of(bindImage(node, scope, templateCode));
        }
        BlockNode block = switch (node.name()) {
            case "heading" -> new HeadingNode(
                    node.attributes().getOrDefault("id", ""),
                    positiveInteger(node.attributes().getOrDefault("level", "1"), "heading.level"),
                    node.attributes().getOrDefault("style", ""),
                    bindInline(node.children(), scope, templateCode, governor));
            case "paragraph" -> new ParagraphNode(
                    node.attributes().getOrDefault("id", ""),
                    node.attributes().getOrDefault("style", ""),
                    bindInline(node.children(), scope, templateCode, governor));
            case "annotation" -> bindAnnotation(node, scope, templateCode, governor);
            case "page-break" -> PageBreakNode.INSTANCE;
            case "table-of-contents" -> new TableOfContentsNode(
                    node.attributes().get("title"),
                    positiveInteger(node.attributes().getOrDefault("min-level", "1"),
                            "table-of-contents.min-level"),
                    positiveInteger(node.attributes().getOrDefault("max-level", "3"),
                            "table-of-contents.max-level"));
            default -> throw PrintValidationException.invalidDocument(
                    "不支持的基础块标签：" + node.name());
        };
        governor.addNodes(1);
        return List.of(block);
    }

    /** 将受控属性和纯文本内容绑定为不含 PDF 类型的批注节点。 */
    private AnnotationNode bindAnnotation(
            CompiledXmlNode node,
            BindingScope scope,
            String templateCode,
            BindingGovernor governor) {
        AnnotationType type = switch (node.attributes().get("type").toLowerCase(Locale.ROOT)) {
            case "text-note" -> AnnotationType.TEXT_NOTE;
            case "free-text" -> AnnotationType.FREE_TEXT;
            default -> throw bindingError(templateCode, node, "批注类型不受支持");
        };
        AnnotationPlacement placement = switch (node.attributes()
                .getOrDefault("placement", "top-right").toLowerCase(Locale.ROOT)) {
            case "top-left" -> AnnotationPlacement.TOP_LEFT;
            case "top-right" -> AnnotationPlacement.TOP_RIGHT;
            case "bottom-left" -> AnnotationPlacement.BOTTOM_LEFT;
            case "bottom-right" -> AnnotationPlacement.BOTTOM_RIGHT;
            default -> throw bindingError(templateCode, node, "批注方位不受支持");
        };
        String defaultWidth = type == AnnotationType.TEXT_NOTE ? "6mm" : "50mm";
        String defaultHeight = type == AnnotationType.TEXT_NOTE ? "6mm" : "20mm";
        return new AnnotationNode(
                type,
                node.attributes().get("target"),
                placement,
                annotationMillimeters(node.attributes().getOrDefault("width", defaultWidth), "annotation.width"),
                annotationMillimeters(node.attributes().getOrDefault("height", defaultHeight), "annotation.height"),
                annotationMillimeters(node.attributes().getOrDefault("offset-x", "0mm"), "annotation.offset-x"),
                annotationMillimeters(node.attributes().getOrDefault("offset-y", "0mm"), "annotation.offset-y"),
                node.attributes().getOrDefault("author", ""),
                bindAnnotationContent(node.children(), scope, templateCode, governor));
    }

    /** 按模板顺序拼接批注允许的直接文本、text 和 field。 */
    private String bindAnnotationContent(
            List<CompiledXmlNode> nodes,
            BindingScope scope,
            String templateCode,
            BindingGovernor governor) {
        StringBuilder content = new StringBuilder();
        for (CompiledXmlNode node : nodes) {
            String text;
            if ("#text".equals(node.name()) || "text".equals(node.name())) {
                text = node.text();
            } else if ("field".equals(node.name())) {
                text = fieldText(node, scope, templateCode);
            } else {
                throw bindingError(templateCode, node, "annotation 只能包含文本和 field");
            }
            content.append(text);
            governor.addText(text.length());
        }
        return content.toString();
    }

    /** 绑定静态或动态逻辑资源为不执行 IO 的图片描述。 */
    private ImageNode bindImage(
            CompiledXmlNode node, BindingScope scope, String templateCode) {
        String resourceId = node.attributes().get("resource-id");
        if (resourceId == null) {
            BindingScope.ResolvedValue resolved = scope.resolve(node.dataPath());
            if (resolved.isInvalid()) {
                throw bindingError(templateCode, node, "图片资源路径无法继续遍历");
            }
            if (!resolved.isPresent()) {
                throw bindingError(templateCode, node, "图片资源路径不存在");
            }
            JsonNode value = resolved.value();
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw bindingError(templateCode, node, "图片资源路径必须指向非空字符串");
            }
            resourceId = value.textValue();
        }
        return new ImageNode(
                node.attributes().getOrDefault("id", ""), resourceId,
                node.attributes().get("alt"),
                positiveMillimeters(node.attributes().get("width"), "image.width"),
                positiveMillimeters(node.attributes().get("height"), "image.height"));
    }

    /** 绑定表头、表体和动态行，并在无行时剪枝整个表格。 */
    private List<BlockNode> bindTable(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<TableRow> headerRows = List.of();
        List<TableRow> bodyRows = List.of();
        for (CompiledXmlNode section : node.children()) {
            if ("header".equals(section.name())) {
                headerRows = bindTableRows(
                        section.children(), scope, templateCode, governor);
            } else if ("body".equals(section.name())) {
                bodyRows = bindTableRows(
                        section.children(), scope, templateCode, governor);
            }
        }
        if (headerRows.isEmpty() && bodyRows.isEmpty()) {
            return List.of();
        }
        List<TableRow> rows = new ArrayList<>(headerRows.size() + bodyRows.size());
        rows.addAll(headerRows);
        rows.addAll(bodyRows);
        TableNode table = new TableNode(
                node.attributes().getOrDefault("id", ""),
                node.attributes().getOrDefault("style", ""), headerRows.size(), rows);
        governor.addNodes(1);
        return List.of(table);
    }

    /** 绑定表格行结果域中的静态行、条件和循环。 */
    private List<TableRow> bindTableRows(
            List<CompiledXmlNode> nodes, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<TableRow> rows = new ArrayList<>();
        for (CompiledXmlNode node : nodes) {
            if ("row".equals(node.name())) {
                rows.add(bindTableRow(node, scope, templateCode, governor));
            } else if ("if".equals(node.name())) {
                rows.addAll(bindCondition(
                        node, scope, templateCode, governor, this::bindTableRows));
            } else if ("for-each".equals(node.name())) {
                rows.addAll(bindLoop(
                        node, scope, templateCode, governor, this::bindTableRows));
            } else {
                throw bindingError(templateCode, node, "表格行结果域只能包含 row");
            }
        }
        return List.copyOf(rows);
    }

    /**
     * 在统一动态深度和预算范围内绑定条件分支。
     *
     * @param node 已编译条件节点
     * @param scope 当前只读绑定作用域
     * @param templateCode 当前模板代码
     * @param governor 当前绑定预算治理器
     * @param binder 当前结果域的递归绑定函数
     * @param <T> 块节点或表格行结果类型
     * @return 条件选中分支生成的不可变结果
     */
    private <T> List<T> bindCondition(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor, NodeListBinder<T> binder) {
        governor.enterDynamic();
        try {
            governor.addDynamicOperations(1);
            return binder.bind(
                    conditionBranch(node, matchesCondition(node, scope, templateCode)),
                    scope, templateCode, governor);
        } finally {
            governor.exitDynamic();
        }
    }

    /**
     * 在统一路径、数组和预算约束下展开循环。
     *
     * @param node 已编译循环节点
     * @param scope 当前只读绑定作用域
     * @param templateCode 当前模板代码
     * @param governor 当前绑定预算治理器
     * @param binder 当前结果域的递归绑定函数
     * @param <T> 块节点或表格行结果类型
     * @return 按数组顺序生成的不可变结果
     */
    private <T> List<T> bindLoop(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor, NodeListBinder<T> binder) {
        governor.enterDynamic();
        try {
            BindingScope.ResolvedValue resolved = scope.resolve(node.dataPath());
            if (resolved.isInvalid()) {
                throw bindingError(templateCode, node,
                        "循环数据路径无法继续遍历：" + node.dataPath().displayPath());
            }
            if (!resolved.isPresent()) {
                throw bindingError(templateCode, node,
                        "循环数据路径不存在：" + node.dataPath().displayPath());
            }
            JsonNode value = resolved.value();
            if (value.isNull()) {
                return List.of();
            }
            if (!value.isArray()) {
                throw bindingError(templateCode, node,
                        "循环数据路径必须指向数组：" + node.dataPath().displayPath());
            }
            governor.checkLoopItems(value.size());
            governor.addDynamicOperations(value.size());
            List<T> results = new ArrayList<>();
            for (JsonNode item : value) {
                results.addAll(binder.bind(
                        node.children(), scope.child(node.variableName(), item),
                        templateCode, governor));
            }
            return List.copyOf(results);
        } finally {
            governor.exitDynamic();
        }
    }

    /** 求值结构化条件或显式注册的扩展条件表达式。 */
    private boolean matchesCondition(
            CompiledXmlNode node, BindingScope scope, String templateCode) {
        if (node.expressionPlan() == null) {
            return node.condition().matches(
                    scope.resolve(node.condition().path()), node, templateCode);
        }
        try {
            return node.expressionPlan().evaluate(
                    new ExpressionEvaluationContext(scope.dataView()));
        } catch (RuntimeException exception) {
            throw bindingError(templateCode, node, "表达式求值失败", exception);
        }
    }

    /** 绑定一个静态行及其单元格块内容。 */
    private TableRow bindTableRow(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<TableCell> cells = new ArrayList<>(node.children().size());
        for (CompiledXmlNode cell : node.children()) {
            cells.add(new TableCell(
                    cell.attributes().getOrDefault("style", ""),
                    bindBlocks(cell.children(), scope, templateCode, governor),
                    positiveInteger(cell.attributes().getOrDefault("row-span", "1"),
                            "cell.row-span"),
                    positiveInteger(cell.attributes().getOrDefault("col-span", "1"),
                            "cell.col-span")));
            governor.addNodes(1);
        }
        governor.addNodes(1);
        return new TableRow(cells);
    }

    /** 绑定标题或段落中的有序文本。 */
    private List<InlineNode> bindInline(
            List<CompiledXmlNode> nodes, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<InlineNode> inline = new ArrayList<>(nodes.size());
        for (CompiledXmlNode node : nodes) {
            if (node.tagPlan() != null) {
                inline.add(bindCustomInline(node, scope, templateCode, governor));
                continue;
            }
            if ("#text".equals(node.name()) || "text".equals(node.name())) {
                inline.add(new TextNode(
                        node.text(), node.attributes().getOrDefault("style", "")));
                governor.addText(node.text().length());
            } else if ("field".equals(node.name())) {
                String text = fieldText(node, scope, templateCode);
                inline.add(new TextNode(
                        text, node.attributes().getOrDefault("style", "")));
                governor.addText(text.length());
            } else if ("line-break".equals(node.name())) {
                inline.add(LineBreakNode.INSTANCE);
            } else if ("page-number".equals(node.name())) {
                inline.add(new PageNumberNode(node.attributes().getOrDefault("style", "")));
            } else if ("page-count".equals(node.name())) {
                inline.add(new PageCountNode(node.attributes().getOrDefault("style", "")));
            } else if ("bookmark".equals(node.name())) {
                inline.add(new BookmarkNode(
                        node.attributes().get("id"), node.attributes().get("label")));
                governor.addText(node.attributes().get("label").length());
            } else if ("link".equals(node.name())) {
                inline.add(new InternalLinkNode(
                        node.attributes().get("target"),
                        bindLinkLabel(node.children(), scope, templateCode, governor)));
            } else {
                throw PrintValidationException.invalidDocument(
                        "不支持的基础行内标签：" + node.name());
            }
            governor.addNodes(1);
        }
        return List.copyOf(inline);
    }

    /** 绑定一个声明为块级位置的可信自定义标签。 */
    private BlockNode bindCustomBlock(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        if (node.tagPlan().placement() != TagPlacement.BLOCK) {
            throw bindingError(templateCode, node, "自定义标签返回位置不是块级");
        }
        DocumentNode result = bindCustomTag(node, scope, templateCode, governor);
        if (!(result instanceof BlockNode block)) {
            throw bindingError(templateCode, node, "自定义标签返回了错误块节点类型");
        }
        return block;
    }

    /** 绑定一个声明为行内位置的可信自定义标签。 */
    private InlineNode bindCustomInline(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        if (node.tagPlan().placement() != TagPlacement.INLINE) {
            throw bindingError(templateCode, node, "自定义标签返回位置不是行内");
        }
        DocumentNode result = bindCustomTag(node, scope, templateCode, governor);
        if (!(result instanceof InlineNode inline)) {
            throw bindingError(templateCode, node, "自定义标签返回了错误行内节点类型");
        }
        return inline;
    }

    /** 先绑定受控子节点，再对处理器组装的最终树重新执行中央容量治理。 */
    private DocumentNode bindCustomTag(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        governor.markCustomTagUsed();
        BindingGovernor.GeneratedUsage checkpoint = governor.checkpointGeneratedUsage();
        List<BlockNode> blockChildren = List.of();
        List<InlineNode> inlineChildren = List.of();
        TagContentModel contentModel = node.tagPlan().contentModel();
        if (contentModel == TagContentModel.BLOCKS) {
            blockChildren = bindBlocks(node.children(), scope, templateCode, governor);
        } else if (contentModel == TagContentModel.INLINE) {
            inlineChildren = bindInline(node.children(), scope, templateCode, governor);
        } else if (!node.children().isEmpty()) {
            throw bindingError(templateCode, node, "自定义空标签不能包含子节点");
        }
        DocumentNode result;
        try {
            result = node.tagPlan().plan().bind(new TagBindingContext(
                    scope.dataView(), blockChildren, inlineChildren));
        } catch (RuntimeException exception) {
            throw bindingError(templateCode, node, "自定义标签绑定失败", exception);
        }
        if (result == null) {
            throw bindingError(templateCode, node, "自定义标签不能返回 null");
        }
        boolean declaredType = node.tagPlan().inspectionContribution().nodeTypes()
                .contains(result.getClass());
        if (!declaredType) {
            throw bindingError(templateCode, node, "自定义标签返回了未声明的节点类型");
        }
        governor.restoreGeneratedUsage(checkpoint);
        ExtensionNodeGovernor.govern(result, governor, node.tagPlan().idsAllowed());
        return result;
    }

    /** 绑定只允许文本和字段的内部链接标签。 */
    private List<InlineNode> bindLinkLabel(
            List<CompiledXmlNode> nodes, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<InlineNode> label = new ArrayList<>(nodes.size());
        for (CompiledXmlNode node : nodes) {
            String text;
            if ("#text".equals(node.name()) || "text".equals(node.name())) {
                text = node.text();
            } else if ("field".equals(node.name())) {
                text = fieldText(node, scope, templateCode);
            } else {
                throw bindingError(templateCode, node, "link 标签只能包含文本和 field");
            }
            label.add(new TextNode(
                    text, node.attributes().getOrDefault("style", "")));
            governor.addNodes(1);
            governor.addText(text.length());
        }
        return List.copyOf(label);
    }

    /** 解析字段并转换为稳定标量文本。 */
    private String fieldText(
            CompiledXmlNode node, BindingScope scope, String templateCode) {
        BindingScope.ResolvedValue resolved = scope.resolve(node.dataPath());
        if (resolved.isInvalid()) {
            throw bindingError(templateCode, node,
                    "数据路径无法继续遍历：" + node.dataPath().displayPath());
        }
        if (!resolved.isPresent()) {
            throw bindingError(templateCode, node, "数据路径不存在：" + node.dataPath().displayPath());
        }
        JsonNode value = resolved.value();
        if (value.isNull()) {
            return "";
        }
        if (node.formatPlan() != null) {
            try {
                // 格式化器属于公开扩展，交付独立副本，避免扩展改写本次绑定快照。
                String formatted = node.formatPlan().format(value.deepCopy());
                if (formatted == null) {
                    throw bindingError(templateCode, node, "格式化器返回了空文本");
                }
                return formatted;
            } catch (RuntimeException exception) {
                throw bindingError(
                        templateCode, node, "字段值无法按已编译格式输出", exception);
            }
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        throw bindingError(templateCode, node, "字段路径必须指向标量值：" + node.dataPath().displayPath());
    }

    /** 创建不包含业务值的安全绑定异常。 */
    private PrintValidationException bindingError(
            String templateCode, CompiledXmlNode node, String detail) {
        return XmlDiagnosticExceptions.binding(templateCode, node, detail);
    }

    /** 创建保留原因链但不公开实现消息的安全绑定异常。 */
    private PrintValidationException bindingError(
            String templateCode, CompiledXmlNode node, String detail, Throwable cause) {
        return XmlDiagnosticExceptions.binding(templateCode, node, detail, cause);
    }

    /** 解析严格正整数属性。 */
    private int positiveInteger(String value, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw PrintValidationException.invalidDocument(name + " 必须为正整数");
        }
    }

    /** 解析最多三位小数的毫米尺寸并转换为整数微米。 */
    private int millimeters(String value) {
        if (!StrictXmlMillimeterValue.isUnsigned(value)) {
            throw PrintValidationException.invalidDocument("页面边距必须使用非负 mm 单位");
        }
        try {
            return StrictXmlMillimeterValue.toMicrometers(value);
        } catch (ArithmeticException exception) {
            throw PrintValidationException.invalidDocument("页面边距超出支持范围");
        }
    }

    /** 解析编译阶段已经验证的正毫米尺寸。 */
    private int positiveMillimeters(String value, String name) {
        int micrometers = millimeters(value);
        if (micrometers < 1) {
            throw PrintValidationException.invalidDocument(name + " 必须大于零");
        }
        return micrometers;
    }

    /** 选择 then 或 else 的内容，未声明 else 时返回空结果。 */
    private List<CompiledXmlNode> conditionBranch(
            CompiledXmlNode condition, boolean matched) {
        if (matched) {
            return condition.children().get(0).children();
        }
        return condition.children().size() == 2
                ? condition.children().get(1).children() : List.of();
    }

    /** 解析编译阶段已经验证的带方向毫米值。 */
    private int annotationMillimeters(String value, String name) {
        if (!StrictXmlMillimeterValue.isSigned(value)) {
            throw PrintValidationException.invalidDocument(name + " 必须使用 mm 单位");
        }
        try {
            return StrictXmlMillimeterValue.toMicrometers(value);
        } catch (ArithmeticException exception) {
            throw PrintValidationException.invalidDocument(name + " 超出支持范围");
        }
    }

    /**
     * 统一表示块节点和表格行的递归绑定入口。
     *
     * @param <T> 当前结果域的节点类型
     */
    @FunctionalInterface
    private interface NodeListBinder<T> {

        /**
         * 绑定当前结果域中的节点列表。
         *
         * @param nodes 待绑定的已编译节点
         * @param scope 当前只读绑定作用域
         * @param templateCode 当前模板代码
         * @param governor 当前绑定预算治理器
         * @return 不可变绑定结果
         */
        List<T> bind(
                List<CompiledXmlNode> nodes,
                BindingScope scope,
                String templateCode,
                BindingGovernor governor);
    }
}
