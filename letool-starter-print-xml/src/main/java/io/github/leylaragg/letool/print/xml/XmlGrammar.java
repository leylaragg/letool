package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.xml.tag.PrintTagHandler;
import io.github.leylaragg.letool.print.xml.tag.PrintTagRegistry;
import io.github.leylaragg.letool.print.xml.tag.TagContentModel;
import io.github.leylaragg.letool.print.xml.tag.TagPlacement;

import javax.xml.stream.XMLStreamReader;
import java.util.Map;
import java.util.Set;

/**
 * 维护 XML DSL 的标签、父子关系和内容模型。
 *
 * @author leyland
 */
final class XmlGrammar {

    /** 每个内置标签允许出现的属性。 */
    private static final Map<String, Set<String>> ALLOWED_ATTRIBUTES = Map.ofEntries(
            Map.entry("document", Set.of(
                    "context-version", "outputs", "title", "author", "language")),
            Map.entry("fragment", Set.of("parameters")),
            Map.entry("include", Set.of("template")),
            Map.entry("with", Set.of("name", "path")),
            Map.entry("styles", Set.of()),
            Map.entry("text-style", Set.of("name", "font-family", "font-size", "font-weight",
                    "color", "line-height", "decorations")),
            Map.entry("paragraph-style", Set.of("name", "text-style", "alignment",
                    "first-line-indent", "left-indent", "right-indent", "spacing-before",
                    "spacing-after", "whitespace", "wrap", "keep-together")),
            Map.entry("table-style", Set.of("name", "width", "layout", "column-widths",
                    "repeat-header", "page-break")),
            Map.entry("cell-style", Set.of(
                    "name", "background", "padding", "vertical-alignment")),
            Map.entry("border", Set.of("side", "line", "width", "color")),
            Map.entry("page", Set.of("size", "orientation", "margin", "margin-top",
                    "margin-right", "margin-bottom", "margin-left", "numbering",
                    "start-page-number")),
            Map.entry("page-header", Set.of()),
            Map.entry("page-body", Set.of()),
            Map.entry("page-footer", Set.of()),
            Map.entry("section", Set.of("id")),
            Map.entry("heading", Set.of("id", "level", "style")),
            Map.entry("paragraph", Set.of("id", "style")),
            Map.entry("annotation", Set.of("type", "target", "placement", "width", "height",
                    "offset-x", "offset-y", "author")),
            Map.entry("table", Set.of("id", "style")),
            Map.entry("header", Set.of()),
            Map.entry("body", Set.of()),
            Map.entry("row", Set.of()),
            Map.entry("cell", Set.of("row-span", "col-span", "style")),
            Map.entry("image", Set.of("id", "resource-id", "resource-path", "alt", "width", "height")),
            Map.entry("bookmark", Set.of("id", "label")),
            Map.entry("link", Set.of("target")),
            Map.entry("text", Set.of("style")),
            Map.entry("field", Set.of("path", "formatter", "style")),
            Map.entry("format-option", Set.of("name", "value")),
            Map.entry("if", Set.of("path", "operator", "value", "value-type",
                    "expression-language", "test")),
            Map.entry("then", Set.of()),
            Map.entry("else", Set.of()),
            Map.entry("for-each", Set.of("items", "var")),
            Map.entry("line-break", Set.of()),
            Map.entry("page-number", Set.of("style")),
            Map.entry("page-count", Set.of("style")),
            Map.entry("page-break", Set.of()),
            Map.entry("table-of-contents", Set.of("title", "min-level", "max-level")));

    /** 普通块级容器可以直接承载的标签。 */
    private static final Set<String> BLOCK_CHILDREN = Set.of(
            "section", "heading", "paragraph", "annotation", "table", "image",
            "page-break", "if", "for-each", "include");

    /** 页面区域额外允许声明目录。 */
    private static final Set<String> PAGE_CHILDREN = Set.of(
            "section", "heading", "paragraph", "annotation", "table", "image",
            "page-break", "if", "for-each", "include", "table-of-contents");

    /** 动态块还可以在表格结果域中承载行。 */
    private static final Set<String> DYNAMIC_BLOCK_CHILDREN = Set.of(
            "section", "heading", "paragraph", "annotation", "table", "image",
            "page-break", "row", "if", "for-each", "include");

    /** include 和块级扩展允许出现的位置。 */
    private static final Set<String> BLOCK_CONTAINERS = Set.of(
            "fragment", "page-header", "page-body", "page-footer",
            "section", "cell", "then", "else", "for-each");

    /** 每个内置父标签允许包含的直接子标签。 */
    private static final Map<String, Set<String>> ALLOWED_CHILDREN = Map.ofEntries(
            Map.entry("document", Set.of("styles", "page")),
            Map.entry("fragment", BLOCK_CHILDREN),
            Map.entry("styles", Set.of(
                    "text-style", "paragraph-style", "table-style", "cell-style")),
            Map.entry("text-style", Set.of()),
            Map.entry("paragraph-style", Set.of()),
            Map.entry("table-style", Set.of()),
            Map.entry("cell-style", Set.of("border")),
            Map.entry("border", Set.of()),
            Map.entry("page", Set.of("page-header", "page-body", "page-footer")),
            Map.entry("page-header", PAGE_CHILDREN),
            Map.entry("page-body", PAGE_CHILDREN),
            Map.entry("page-footer", PAGE_CHILDREN),
            Map.entry("section", BLOCK_CHILDREN),
            Map.entry("heading", Set.of("text", "field", "bookmark", "link",
                    "line-break", "page-number", "page-count")),
            Map.entry("paragraph", Set.of("text", "field", "bookmark", "link",
                    "line-break", "page-number", "page-count")),
            Map.entry("annotation", Set.of("text", "field")),
            Map.entry("table", Set.of("header", "body")),
            Map.entry("header", Set.of("row", "if", "for-each")),
            Map.entry("body", Set.of("row", "if", "for-each")),
            Map.entry("row", Set.of("cell")),
            Map.entry("cell", BLOCK_CHILDREN),
            Map.entry("image", Set.of()),
            Map.entry("bookmark", Set.of()),
            Map.entry("link", Set.of("text", "field")),
            Map.entry("text", Set.of()),
            Map.entry("field", Set.of("format-option")),
            Map.entry("format-option", Set.of()),
            Map.entry("line-break", Set.of()),
            Map.entry("page-number", Set.of()),
            Map.entry("page-count", Set.of()),
            Map.entry("if", Set.of("then", "else")),
            Map.entry("then", DYNAMIC_BLOCK_CHILDREN),
            Map.entry("else", DYNAMIC_BLOCK_CHILDREN),
            Map.entry("for-each", DYNAMIC_BLOCK_CHILDREN),
            Map.entry("include", Set.of("with")),
            Map.entry("with", Set.of()),
            Map.entry("page-break", Set.of()),
            Map.entry("table-of-contents", Set.of()));

    /** 可以直接保存文本内容的标签。 */
    private static final Set<String> TEXT_CONTAINERS =
            Set.of("heading", "paragraph", "annotation", "text", "link");

    /** 编译器当前启用的可信标签扩展。 */
    private final PrintTagRegistry tagRegistry;

    /** 使用一份不可变标签注册表建立语法。 */
    XmlGrammar(PrintTagRegistry tagRegistry) {
        this.tagRegistry = tagRegistry;
    }

    /** 校验当前开始标签的命名空间、名称及父子关系。 */
    String validateStartElement(
            TemplateDefinition definition, XMLStreamReader reader, String parent) {
        String templateCode = definition.template().templateCode();
        if (!XmlDsl.NAMESPACE_V1.equals(reader.getNamespaceURI())) {
            throw located(templateCode, reader, "命名空间不受支持");
        }
        String name = reader.getLocalName();
        if (!ALLOWED_ATTRIBUTES.containsKey(name) && customTag(name) == null) {
            throw located(templateCode, reader, "未知标签：" + name);
        }
        if (parent == null) {
            String expected = definition.type() == TemplateType.DOCUMENT ? "document" : "fragment";
            if (!expected.equals(name)) {
                throw located(templateCode, reader, "根标签必须为 " + expected);
            }
        } else if (!allowsChild(parent, name)) {
            throw located(templateCode, reader, parent + " 不能包含 " + name);
        }
        return name;
    }

    /** 返回标签允许的属性，调用前已经确认标签存在。 */
    Set<String> allowedAttributes(String name) {
        PrintTagHandler handler = customTag(name);
        return handler == null ? ALLOWED_ATTRIBUTES.get(name) : handler.allowedAttributes();
    }

    /** @return 当前标签是否可以直接保存行内文本 */
    boolean acceptsDirectText(String name) {
        PrintTagHandler handler = customTag(name);
        return TEXT_CONTAINERS.contains(name)
                || handler != null && handler.contentModel() == TagContentModel.INLINE;
    }

    /** @return 当前标签是否声明为空内容模型 */
    boolean isEmptyCustomTag(String name) {
        PrintTagHandler handler = customTag(name);
        return handler != null && handler.contentModel() == TagContentModel.EMPTY;
    }

    /** 在节点闭合时校验依赖完整子列表的结构约束。 */
    void validateCompletedNode(String templateCode, ParsedXmlNode node) {
        String name = node.name();
        if (Set.of("page-break", "format-option", "with", "image", "bookmark",
                "line-break", "page-number", "page-count", "text-style",
                "paragraph-style", "table-style", "border").contains(name)
                && !node.children().isEmpty()) {
            throw located(templateCode, node, name + " 必须为空元素");
        }
        if ("section".equals(name) && node.children().isEmpty()) {
            throw located(templateCode, node, "section 至少包含一个块节点");
        }
        if (isEmptyCustomTag(name) && !node.children().isEmpty()) {
            throw located(templateCode, node, "自定义空标签不能包含子节点");
        }
        if ("table".equals(name)) {
            validateTableStructure(templateCode, node);
        } else if ("page".equals(name)) {
            validatePageStructure(templateCode, node);
        } else if ("if".equals(name)) {
            validateIfStructure(templateCode, node);
        }
        if ("row".equals(name) && node.children().isEmpty()) {
            throw located(templateCode, node, "row 至少包含一个 cell");
        }
        if ("link".equals(name) && node.children().isEmpty()) {
            throw located(templateCode, node, "link 标签不能为空");
        }
        if ("annotation".equals(name) && lacksVisibleContent(node)) {
            throw located(templateCode, node, "annotation 内容不能为空");
        }
        if ("heading".equals(name) && lacksHeadingContent(node)) {
            throw located(templateCode, node, "heading 内容不能为空");
        }
    }

    /** 查找已经显式注册的自定义标签。 */
    private PrintTagHandler customTag(String name) {
        return tagRegistry.tagNames().contains(name) ? tagRegistry.require(name) : null;
    }

    /** 校验内置父子关系或自定义标签声明的放置与内容模型。 */
    private boolean allowsChild(String parent, String child) {
        if ("include".equals(child)) {
            return BLOCK_CONTAINERS.contains(parent);
        }
        PrintTagHandler parentHandler = customTag(parent);
        PrintTagHandler childHandler = customTag(child);
        if (parentHandler != null) {
            return switch (parentHandler.contentModel()) {
                case EMPTY -> false;
                case BLOCKS -> isBlockChild(child, childHandler);
                case INLINE -> isInlineChild(child, childHandler);
            };
        }
        Set<String> builtInChildren = ALLOWED_CHILDREN.get(parent);
        if (builtInChildren != null && builtInChildren.contains(child)) {
            return true;
        }
        if (childHandler == null) {
            return false;
        }
        return childHandler.placement() == TagPlacement.BLOCK
                ? BLOCK_CONTAINERS.contains(parent)
                : Set.of("heading", "paragraph").contains(parent);
    }

    /** 判断一个子标签能否产生块级节点。 */
    private boolean isBlockChild(String name, PrintTagHandler handler) {
        return handler == null ? BLOCK_CHILDREN.contains(name)
                : handler.placement() == TagPlacement.BLOCK;
    }

    /** 判断一个子标签能否产生行内节点。 */
    private boolean isInlineChild(String name, PrintTagHandler handler) {
        return handler == null
                ? Set.of("text", "field", "bookmark", "link",
                        "line-break", "page-number", "page-count").contains(name)
                : handler.placement() == TagPlacement.INLINE;
    }

    /** 校验表头可选、表体必需且顺序固定。 */
    private void validateTableStructure(String templateCode, ParsedXmlNode table) {
        int bodyCount = 0;
        int headerCount = 0;
        boolean bodySeen = false;
        for (ParsedXmlNode child : table.children()) {
            if ("body".equals(child.name())) {
                bodyCount++;
                bodySeen = true;
            } else if ("header".equals(child.name())) {
                headerCount++;
                if (bodySeen) {
                    throw located(templateCode, table, "table 的 header 顺序必须位于 body 之前");
                }
            }
        }
        if (bodyCount != 1 || headerCount > 1) {
            throw located(templateCode, table, "table 必须包含一个 body，且至多包含一个 header");
        }
    }

    /** 校验页眉、正文和页脚数量及固定顺序。 */
    private void validatePageStructure(String templateCode, ParsedXmlNode page) {
        int bodyCount = 0;
        int headerCount = 0;
        int footerCount = 0;
        int lastOrder = 0;
        for (ParsedXmlNode child : page.children()) {
            int order = switch (child.name()) {
                case "page-header" -> {
                    headerCount++;
                    yield 1;
                }
                case "page-body" -> {
                    bodyCount++;
                    yield 2;
                }
                case "page-footer" -> {
                    footerCount++;
                    yield 3;
                }
                default -> throw located(templateCode, page, "page 包含未知区域");
            };
            if (order < lastOrder) {
                throw located(templateCode, page, "页面区域必须按页眉、正文、页脚排列");
            }
            lastOrder = order;
        }
        if (bodyCount != 1 || headerCount > 1 || footerCount > 1) {
            throw located(templateCode, page, "page 必须包含一个正文，且页眉页脚各至多一个");
        }
    }

    /** 校验条件节点使用唯一 then 和可选 else。 */
    private void validateIfStructure(String templateCode, ParsedXmlNode condition) {
        if (condition.children().isEmpty() || !"then".equals(condition.children().get(0).name())) {
            throw located(templateCode, condition, "if 必须先声明 then");
        }
        if (condition.children().size() > 2
                || condition.children().size() == 2
                && !"else".equals(condition.children().get(1).name())) {
            throw located(templateCode, condition, "if 只能包含一个 then 和一个可选 else");
        }
        if (condition.children().stream().anyMatch(child -> child.children().isEmpty())) {
            throw located(templateCode, condition, "条件分支至少包含一个内容节点");
        }
    }

    /** @return 批注是否没有任何可绑定内容 */
    private boolean lacksVisibleContent(ParsedXmlNode node) {
        return node.children().stream().noneMatch(child -> "field".equals(child.name()))
                && node.children().stream()
                .filter(child -> "#text".equals(child.name()) || "text".equals(child.name()))
                .allMatch(child -> child.text().isBlank());
    }

    /** @return 标题是否没有文字、字段、书签或链接 */
    private boolean lacksHeadingContent(ParsedXmlNode node) {
        return node.children().stream().noneMatch(child -> Set.of(
                        "field", "bookmark", "link").contains(child.name()))
                && node.children().stream()
                .filter(child -> "#text".equals(child.name()) || "text".equals(child.name()))
                .allMatch(child -> child.text().isBlank());
    }

    /** 创建只带安全模板代码和行列位置的语法异常。 */
    private PrintCompilationException located(
            String templateCode, XMLStreamReader reader, String detail) {
        return PrintCompilationException.invalid(
                templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                        + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                        + " 列：" + detail);
    }

    /** 创建只带安全模板代码和节点位置的语法异常。 */
    private PrintCompilationException located(
            String templateCode, ParsedXmlNode node, String detail) {
        return PrintCompilationException.invalid(
                templateCode + "：第 " + node.line() + " 行，第 " + node.column()
                        + " 列：" + detail);
    }
}
