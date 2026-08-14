package com.github.leyland.letool.print.xml;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.Margins;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.PageOrientation;
import com.github.leyland.letool.print.document.PageSize;
import com.github.leyland.letool.print.document.node.BlockNode;
import com.github.leyland.letool.print.document.node.BookmarkNode;
import com.github.leyland.letool.print.document.node.DocumentNode;
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.ImageNode;
import com.github.leyland.letool.print.document.node.InlineNode;
import com.github.leyland.letool.print.document.node.InternalLinkNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.TableCell;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableRow;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.xml.expression.ExpressionEvaluationContext;
import com.github.leyland.letool.print.xml.tag.TagBindingContext;
import com.github.leyland.letool.print.xml.tag.TagContentModel;
import com.github.leyland.letool.print.xml.tag.TagPlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将安全 XML 编译快照与只读上下文绑定为通用文档模型。
 *
 * <p>阶段 2C-2 支持基础标签、受限动态结构、复杂节点、格式计划和可信扩展计划。</p>
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
        CompiledXmlNode document = template.root();
        CompiledXmlNode page = document.children().get(0);
        BindingGovernor governor = new BindingGovernor(template.templateCode());
        DocumentModel model = new DocumentModel(
                metadata(document),
                pageLayout(page),
                bindBlocks(page.children(), new BindingScope(context.root()),
                        template.templateCode(), governor));
        try {
            model.validate();
        } catch (PrintValidationException exception) {
            if (governor.customTagUsed()) {
                throw PrintValidationException.invalidDocument(
                        template.templateCode() + "：自定义标签返回的文档模型校验失败");
            }
            throw exception;
        }
        return model;
    }

    /** 绑定可选文档元数据。 */
    private DocumentMetadata metadata(CompiledXmlNode document) {
        return new DocumentMetadata(
                document.attributes().get("title"),
                document.attributes().get("author"),
                document.attributes().get("language"));
    }

    /** 绑定物理页面布局。 */
    private PageLayout pageLayout(CompiledXmlNode page) {
        String size = page.attributes().getOrDefault("size", "A4").toUpperCase(Locale.ROOT);
        PageSize pageSize = switch (size) {
            case "A4" -> PageSize.A4;
            case "LETTER" -> PageSize.LETTER;
            default -> throw PrintValidationException.invalidDocument("不支持的页面尺寸：" + size);
        };
        String orientationValue = page.attributes()
                .getOrDefault("orientation", "portrait")
                .toLowerCase(Locale.ROOT);
        PageOrientation orientation = switch (orientationValue) {
            case "portrait" -> PageOrientation.PORTRAIT;
            case "landscape" -> PageOrientation.LANDSCAPE;
            default -> throw PrintValidationException.invalidDocument(
                    "不支持的页面方向：" + orientationValue);
        };
        int margin = millimeters(page.attributes().getOrDefault("margin", "20mm"));
        return new PageLayout(pageSize, orientation, new Margins(margin, margin, margin, margin));
    }

    /** 绑定有序块级节点列表。 */
    private List<BlockNode> bindBlocks(
            List<CompiledXmlNode> nodes, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<BlockNode> blocks = new ArrayList<>(nodes.size());
        for (CompiledXmlNode node : nodes) {
            if ("if".equals(node.name())) {
                governor.enterDynamic();
                try {
                    governor.addDynamicOperations(1);
                    if (matchesCondition(node, scope, templateCode)) {
                        blocks.addAll(bindBlocks(
                                node.children(), scope, templateCode, governor));
                    }
                } finally {
                    governor.exitDynamic();
                }
            } else if ("for-each".equals(node.name())) {
                governor.enterDynamic();
                try {
                    blocks.addAll(bindLoop(node, scope, templateCode, governor));
                } finally {
                    governor.exitDynamic();
                }
            } else {
                blocks.addAll(bindBlock(node, scope, templateCode, governor));
            }
        }
        return List.copyOf(blocks);
    }

    /** 按数组顺序展开一个块级循环。 */
    private List<BlockNode> bindLoop(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
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
        List<BlockNode> blocks = new ArrayList<>();
        for (JsonNode item : value) {
            blocks.addAll(bindBlocks(
                    node.children(), scope.child(node.variableName(), item), templateCode, governor));
        }
        return List.copyOf(blocks);
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
                    bindInline(node.children(), scope, templateCode, governor));
            case "paragraph" -> new ParagraphNode(
                    node.attributes().getOrDefault("id", ""),
                    bindInline(node.children(), scope, templateCode, governor));
            case "page-break" -> PageBreakNode.INSTANCE;
            default -> throw PrintValidationException.invalidDocument(
                    "不支持的基础块标签：" + node.name());
        };
        governor.addNodes(1);
        return List.of(block);
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
                node.attributes().getOrDefault("id", ""), headerRows.size(), rows);
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
                governor.enterDynamic();
                try {
                    governor.addDynamicOperations(1);
                    if (matchesCondition(node, scope, templateCode)) {
                        rows.addAll(bindTableRows(
                                node.children(), scope, templateCode, governor));
                    }
                } finally {
                    governor.exitDynamic();
                }
            } else if ("for-each".equals(node.name())) {
                governor.enterDynamic();
                try {
                    rows.addAll(bindTableLoop(node, scope, templateCode, governor));
                } finally {
                    governor.exitDynamic();
                }
            } else {
                throw bindingError(templateCode, node, "表格行结果域只能包含 row");
            }
        }
        return List.copyOf(rows);
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
            throw bindingError(templateCode, node, "表达式求值失败");
        }
    }

    /** 按数组顺序展开表格行循环。 */
    private List<TableRow> bindTableLoop(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
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
        List<TableRow> rows = new ArrayList<>();
        for (JsonNode item : value) {
            rows.addAll(bindTableRows(
                    node.children(), scope.child(node.variableName(), item),
                    templateCode, governor));
        }
        return List.copyOf(rows);
    }

    /** 绑定一个静态行及其单元格块内容。 */
    private TableRow bindTableRow(
            CompiledXmlNode node, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<TableCell> cells = new ArrayList<>(node.children().size());
        for (CompiledXmlNode cell : node.children()) {
            cells.add(new TableCell(
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
                inline.add(new TextNode(node.text()));
                governor.addText(node.text().length());
            } else if ("field".equals(node.name())) {
                String text = fieldText(node, scope, templateCode);
                inline.add(new TextNode(text));
                governor.addText(text.length());
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
            throw bindingError(templateCode, node, "自定义标签绑定失败");
        }
        if (result == null) {
            throw bindingError(templateCode, node, "自定义标签不能返回 null");
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
            label.add(new TextNode(text));
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
                String formatted = node.formatPlan().format(value);
                if (formatted == null) {
                    throw bindingError(templateCode, node, "格式化器返回了空文本");
                }
                return formatted;
            } catch (RuntimeException exception) {
                throw bindingError(templateCode, node, "字段值无法按已编译格式输出");
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
        return PrintValidationException.invalidDocument(
                templateCode + "：" + node.tagPath() + "，第 " + node.line()
                        + " 行，第 " + node.column() + " 列：" + detail);
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
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.matches("(?:0|[1-9][0-9]{0,3})(?:\\.[0-9]{1,3})?mm")) {
            throw PrintValidationException.invalidDocument("页面边距必须使用非负 mm 单位");
        }
        String number = normalized.substring(0, normalized.length() - 2);
        try {
            return new java.math.BigDecimal(number)
                    .movePointRight(3)
                    .intValueExact();
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
}
