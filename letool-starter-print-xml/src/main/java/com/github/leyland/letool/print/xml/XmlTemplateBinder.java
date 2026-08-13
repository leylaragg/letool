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
import com.github.leyland.letool.print.document.node.HeadingNode;
import com.github.leyland.letool.print.document.node.InlineNode;
import com.github.leyland.letool.print.document.node.PageBreakNode;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.SectionNode;
import com.github.leyland.letool.print.document.node.TextNode;
import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将安全 XML 编译快照与只读上下文绑定为通用文档模型。
 *
 * <p>阶段 2B 支持静态基础标签、受限字段、结构化条件和词法作用域循环。</p>
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
        model.validate();
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
                    if (node.condition().matches(
                            scope.resolve(node.condition().path()), node, templateCode)) {
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

    /** 绑定标题或段落中的有序文本。 */
    private List<InlineNode> bindInline(
            List<CompiledXmlNode> nodes, BindingScope scope, String templateCode,
            BindingGovernor governor) {
        List<InlineNode> inline = new ArrayList<>(nodes.size());
        for (CompiledXmlNode node : nodes) {
            if ("#text".equals(node.name()) || "text".equals(node.name())) {
                inline.add(new TextNode(node.text()));
                governor.addText(node.text().length());
            } else if ("field".equals(node.name())) {
                String text = fieldText(node, scope, templateCode);
                inline.add(new TextNode(text));
                governor.addText(text.length());
            } else {
                throw PrintValidationException.invalidDocument(
                        "不支持的基础行内标签：" + node.name());
            }
            governor.addNodes(1);
        }
        return List.copyOf(inline);
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
}
