package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.template.TemplateDefinition;
import com.github.leyland.letool.print.template.TemplateType;
import com.github.leyland.letool.print.xml.format.BuiltInPrintFormatters;
import com.github.leyland.letool.print.xml.format.FormatCompileContext;
import com.github.leyland.letool.print.xml.format.PrintFormatPlan;
import com.github.leyland.letool.print.xml.format.PrintFormatterRegistry;
import com.github.leyland.letool.print.xml.format.PrintValueFormatter;
import com.github.leyland.letool.print.xml.expression.ExpressionCompileContext;
import com.github.leyland.letool.print.xml.expression.PrintConditionExpression;
import com.github.leyland.letool.print.xml.expression.PrintExpressionPlan;
import com.github.leyland.letool.print.xml.expression.PrintExpressionRegistry;
import com.github.leyland.letool.print.xml.tag.PrintTagRegistry;
import com.github.leyland.letool.print.xml.tag.PrintTagHandler;
import com.github.leyland.letool.print.xml.tag.PrintTagPlan;
import com.github.leyland.letool.print.xml.tag.TagCompileContext;
import com.github.leyland.letool.print.xml.tag.TagContentModel;
import com.github.leyland.letool.print.xml.tag.TagPlacement;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

    /** 解析器建立前直接拒绝的 XML 外部访问和声明标记。 */
    private static final Pattern XML_SECURITY_MARKER = Pattern.compile(
            "(?is)<!--.*?-->|<!\\[CDATA\\[.*?]]>|<!DOCTYPE|<!ENTITY|<\\?(?!xml\\s)");

    /** 受控 DSL 不允许出现的脚本或任意表达式标记。 */
    private static final Pattern EXECUTABLE_EXPRESSION = Pattern.compile(
            "(?is)<%|%>|\\$\\{|#\\{|javascript\\s*:|groovy\\s*:");

    /** 拒绝任何外部实体或资源解析请求。 */
    private static final XMLResolver REJECTING_RESOLVER =
            (publicId, systemId, baseUri, namespace) -> {
                throw new XMLStreamException("外部 XML 资源访问被禁止");
            };

    /** 每个标签允许出现的属性。 */
    private static final Map<String, Set<String>> ALLOWED_ATTRIBUTES = Map.ofEntries(
            Map.entry("document", Set.of("context-version", "title", "author", "language")),
            Map.entry("fragment", Set.of()),
            Map.entry("include", Set.of("template")),
            Map.entry("page", Set.of("size", "orientation", "margin")),
            Map.entry("section", Set.of("id")),
            Map.entry("heading", Set.of("id", "level")),
            Map.entry("paragraph", Set.of("id")),
            Map.entry("annotation", Set.of("type", "target", "placement", "width", "height",
                    "offset-x", "offset-y", "author")),
            Map.entry("table", Set.of("id")),
            Map.entry("header", Set.of()),
            Map.entry("body", Set.of()),
            Map.entry("row", Set.of()),
            Map.entry("cell", Set.of("row-span", "col-span")),
            Map.entry("image", Set.of("id", "resource-id", "resource-path", "alt", "width", "height")),
            Map.entry("bookmark", Set.of("id", "label")),
            Map.entry("link", Set.of("target")),
            Map.entry("text", Set.of()),
            Map.entry("field", Set.of("path", "formatter")),
            Map.entry("format-option", Set.of("name", "value")),
            Map.entry("if", Set.of("path", "operator", "value", "value-type",
                    "expression-language", "test")),
            Map.entry("for-each", Set.of("items", "var")),
            Map.entry("page-break", Set.of()));

    /** 普通块级容器可以直接承载的标签。 */
    private static final Set<String> BLOCK_CHILDREN = Set.of(
            "section", "heading", "paragraph", "annotation", "table", "image",
            "page-break", "if", "for-each", "include");

    /** 动态标签除了普通块节点，还可以在表格行结果域中承载 row。 */
    private static final Set<String> DYNAMIC_BLOCK_CHILDREN = Set.of(
            "section", "heading", "paragraph", "annotation", "table", "image",
            "page-break", "row", "if", "for-each", "include");

    /** include 和块级扩展允许出现的位置。 */
    private static final Set<String> BLOCK_CONTAINERS =
            Set.of("fragment", "page", "section", "cell", "if", "for-each");

    /** 每个父标签允许包含的直接子标签。 */
    private static final Map<String, Set<String>> ALLOWED_CHILDREN = Map.ofEntries(
            Map.entry("document", Set.of("page")),
            Map.entry("fragment", BLOCK_CHILDREN),
            Map.entry("page", BLOCK_CHILDREN),
            Map.entry("section", BLOCK_CHILDREN),
            Map.entry("heading", Set.of("text", "field", "bookmark", "link")),
            Map.entry("paragraph", Set.of("text", "field", "bookmark", "link")),
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
            Map.entry("if", DYNAMIC_BLOCK_CHILDREN),
            Map.entry("for-each", DYNAMIC_BLOCK_CHILDREN),
            Map.entry("include", Set.of()),
            Map.entry("page-break", Set.of()));

    /** 允许直接保存文本内容的标签。 */
    private static final Set<String> TEXT_CONTAINERS =
            Set.of("heading", "paragraph", "annotation", "text", "link");

    /** 文档节点逻辑 ID 的稳定安全格式。 */
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    /** 页面边距只接受最多三位小数的非负毫米值。 */
    private static final Pattern MILLIMETERS = Pattern.compile(
            "(?:0|[1-9][0-9]{0,3})(?:\\.[0-9]{1,3})?mm");

    /** 批注偏移允许使用正负毫米值。 */
    private static final Pattern SIGNED_MILLIMETERS = Pattern.compile(
            "[+-]?(?:0|[1-9][0-9]{0,3})(?:\\.[0-9]{1,3})?mm");

    /** 循环变量名的稳定安全格式。 */
    private static final Pattern LOOP_VARIABLE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    /** 格式选项名称的稳定安全格式。 */
    private static final Pattern FORMAT_OPTION_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    /** include 引用使用与模板代码兼容的稳定格式。 */
    private static final Pattern TEMPLATE_REFERENCE = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    /** 编译阶段使用的不可变格式化器注册表。 */
    private final PrintFormatterRegistry formatterRegistry;

    /** 编译阶段使用的不可变表达式注册表。 */
    private final PrintExpressionRegistry expressionRegistry;

    /** 编译阶段使用的不可变自定义标签注册表。 */
    private final PrintTagRegistry tagRegistry;

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
        CompiledXmlNode root = compileDynamicTree(
                template.templateCode(), parsed.root(), "", Set.of(), BindingDomain.BLOCKS);
        return new CompiledXmlTemplate(template.templateCode(), template.dslVersion(),
                template.templateSetVersion(), template.contextVersion(), root);
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
        String source = decodeUtf8(template);
        rejectUnsafeSource(template.templateCode(), source);
        return parse(definition, source);
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
        CompiledXmlNode root = compileDynamicTree(
                template.templateCode(), resolvedRoot, "", Set.of(), BindingDomain.BLOCKS);
        return new CompiledXmlTemplate(template.templateCode(), template.dslVersion(),
                template.templateSetVersion(), template.contextVersion(), root);
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
        List<CompiledXmlNode> blocks = new ArrayList<>(resolvedRoot.children().size());
        for (CompiledXmlNode child : resolvedRoot.children()) {
            blocks.add(compileDynamicTree(
                    templateCode, child, "/fragment", Set.of(), BindingDomain.BLOCKS));
        }
        return new CompiledXmlFragment(templateCode, blocks);
    }

    /** 使用严格 UTF-8 解码，不允许替换非法字节。 */
    private String decodeUtf8(PrintTemplate template) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(template.content()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw PrintCompilationException.invalid(template.templateCode() + "：模板不是合法 UTF-8", exception);
        }
    }

    /** 在 XML 解析前拒绝提供方无关的危险语法。 */
    private void rejectUnsafeSource(String templateCode, String source) {
        java.util.regex.Matcher xmlSecurity = XML_SECURITY_MARKER.matcher(source);
        while (xmlSecurity.find()) {
            String marker = xmlSecurity.group();
            if (!marker.startsWith("<!--") && !marker.startsWith("<![CDATA[")) {
                throw sourceLocated(
                        templateCode,
                        source,
                        xmlSecurity.start(),
                        "模板包含禁止的 XML 声明或外部资源入口");
            }
        }
    }

    /** 根据源码字符偏移计算不包含正文的安全行列位置。 */
    private PrintCompilationException sourceLocated(
            String templateCode,
            String source,
            int offset,
            String detail) {
        int line = 1;
        int column = 1;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return PrintCompilationException.invalid(
                templateCode + "：第 " + line + " 行，第 " + column + " 列：" + detail);
    }

    /** 使用关闭 DTD 和外部实体的 StAX 解析器读取模板。 */
    private ParsedXmlTemplate parse(TemplateDefinition definition, String source) {
        PrintTemplate template = definition.template();
        String templateCode = template.templateCode();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        setRequiredProperty(factory, XMLInputFactory.SUPPORT_DTD, false, templateCode);
        setRequiredProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false, templateCode);
        setRequiredProperty(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false, templateCode);
        setRequiredProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true, templateCode);
        factory.setXMLResolver(REJECTING_RESOLVER);
        try (StringReader input = new StringReader(source)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                Deque<NodeBuilder> stack = new ArrayDeque<>();
                CompiledXmlNode root = null;
                int nodeCount = 0;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamReader.START_ELEMENT) {
                        if (++nodeCount > XmlDsl.MAX_NODE_COUNT) {
                            throw located(templateCode, reader, "节点数量超过 " + XmlDsl.MAX_NODE_COUNT);
                        }
                        if (stack.size() + 1 > XmlDsl.MAX_NODE_DEPTH) {
                            throw located(templateCode, reader, "节点深度超过 " + XmlDsl.MAX_NODE_DEPTH);
                        }
                        String name = validateStartElement(definition, reader, stack);
                        if (!stack.isEmpty()) {
                            stack.peek().flushText(templateCode, reader);
                        }
                        PrintTagHandler customTag = customTag(name);
                        stack.push(new NodeBuilder(
                                name,
                                readAttributes(templateCode, reader),
                                Math.max(reader.getLocation().getLineNumber(), 1),
                                Math.max(reader.getLocation().getColumnNumber(), 1),
                                customTag != null
                                        && customTag.contentModel() == TagContentModel.INLINE,
                                customTag != null
                                        && customTag.contentModel() == TagContentModel.EMPTY));
                    } else if (event == XMLStreamReader.CHARACTERS
                            || event == XMLStreamReader.CDATA
                            || event == XMLStreamReader.SPACE) {
                        if (!stack.isEmpty()) {
                            stack.peek().appendText(reader.getText(), templateCode, reader);
                        }
                    } else if (event == XMLStreamReader.END_ELEMENT) {
                        if (stack.isEmpty()) {
                            throw located(templateCode, reader, "结束标签没有对应的开始标签");
                        }
                        NodeBuilder completed = stack.pop();
                        completed.flushText(templateCode, reader);
                        CompiledXmlNode node = completed.build(templateCode, reader);
                        if (stack.isEmpty()) {
                            if (root != null) {
                                throw located(templateCode, reader, "模板只能包含一个根元素");
                            }
                            root = node;
                        } else {
                            stack.peek().addChild(node);
                        }
                    } else if (event == XMLStreamReader.PROCESSING_INSTRUCTION
                            || event == XMLStreamReader.DTD
                            || event == XMLStreamReader.ENTITY_REFERENCE) {
                        throw located(templateCode, reader, "模板包含禁止的 XML 事件");
                    }
                }
                if (root == null || !stack.isEmpty()) {
                    throw PrintCompilationException.invalid(templateCode + "：模板没有完整根元素");
                }
                if (definition.type() == TemplateType.DOCUMENT) {
                    validateDocument(template, root);
                } else {
                    validateFragment(template, root);
                }
                return new ParsedXmlTemplate(
                        definition.type(), template, root, nodeCount);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException exception) {
            int line = exception.getLocation() == null
                    ? 1 : Math.max(exception.getLocation().getLineNumber(), 1);
            int column = exception.getLocation() == null
                    ? 1 : Math.max(exception.getLocation().getColumnNumber(), 1);
            throw PrintCompilationException.invalid(
                    templateCode + "：第 " + line + " 行，第 " + column + " 列：XML 解析失败",
                    exception);
        }
    }

    /** 校验标签命名空间、名称及父子关系。 */
    private String validateStartElement(
            TemplateDefinition definition,
            XMLStreamReader reader,
            Deque<NodeBuilder> stack) {
        PrintTemplate template = definition.template();
        String templateCode = template.templateCode();
        if (!XmlDsl.NAMESPACE_V1.equals(reader.getNamespaceURI())) {
            throw located(templateCode, reader, "命名空间不受支持");
        }
        String name = reader.getLocalName();
        PrintTagHandler handler = customTag(name);
        if (!ALLOWED_ATTRIBUTES.containsKey(name) && handler == null) {
            throw located(templateCode, reader, "未知标签：" + name);
        }
        if (stack.isEmpty()) {
            String expected = definition.type() == TemplateType.DOCUMENT
                    ? "document" : "fragment";
            if (!expected.equals(name)) {
                throw located(templateCode, reader, "根标签必须为 " + expected);
            }
            return name;
        }
        String parent = stack.peek().name();
        if (!allowsChild(parent, name)) {
            throw located(templateCode, reader, parent + " 不能包含 " + name);
        }
        return name;
    }

    /** 读取并校验无命名空间的白名单属性。 */
    private Map<String, String> readAttributes(String templateCode, XMLStreamReader reader) {
        String element = reader.getLocalName();
        PrintTagHandler handler = customTag(element);
        Set<String> allowed = handler == null
                ? ALLOWED_ATTRIBUTES.get(element) : handler.allowedAttributes();
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            String namespace = reader.getAttributeNamespace(index);
            String name = reader.getAttributeLocalName(index);
            if ((namespace != null && !namespace.isEmpty()) || !allowed.contains(name)) {
                throw located(templateCode, reader, "标签 " + element + " 包含未知属性：" + name);
            }
            String value = reader.getAttributeValue(index);
            rejectDecodedExpression(templateCode, reader, value);
            if (attributes.putIfAbsent(name, value) != null) {
                throw located(templateCode, reader, "标签 " + element + " 包含重复属性：" + name);
            }
        }
        validateStaticAttributes(templateCode, element, attributes, reader);
        return attributes;
    }

    /** 查找已经显式注册的自定义标签，内置标签始终返回空。 */
    private PrintTagHandler customTag(String name) {
        if (!tagRegistry.tagNames().contains(name)) {
            return null;
        }
        return tagRegistry.require(name);
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
        if (childHandler.placement() == TagPlacement.BLOCK) {
            return BLOCK_CONTAINERS.contains(parent);
        }
        return Set.of("heading", "paragraph").contains(parent);
    }

    /** 判断一个子标签能否产生块级节点。 */
    private boolean isBlockChild(String name, PrintTagHandler handler) {
        if (handler != null) {
            return handler.placement() == TagPlacement.BLOCK;
        }
        return BLOCK_CHILDREN.contains(name);
    }

    /** 判断一个子标签能否产生行内节点。 */
    private boolean isInlineChild(String name, PrintTagHandler handler) {
        if (handler != null) {
            return handler.placement() == TagPlacement.INLINE;
        }
        return Set.of("text", "field", "bookmark", "link").contains(name);
    }

    /** 校验不依赖上下文的静态属性，保证发布编译即可发现错误。 */
    private void validateStaticAttributes(
            String templateCode,
            String element,
            Map<String, String> attributes,
            XMLStreamReader reader) {
        validateOptionalText(templateCode, reader, attributes, "title", 256);
        validateOptionalText(templateCode, reader, attributes, "author", 128);
        validateOptionalText(templateCode, reader, attributes, "language", 35);
        String id = attributes.get("id");
        if (id != null && !NODE_ID.matcher(id).matches()) {
            throw located(templateCode, reader, "节点 ID 不合法");
        }
        if ("document".equals(element)) {
            String version = attributes.get("context-version");
            if (version == null || !version.matches("[1-9][0-9]*")) {
                throw located(templateCode, reader, "context-version 必须为正整数");
            }
        }
        if ("include".equals(element)) {
            String target = attributes.get("template");
            if (target == null || !TEMPLATE_REFERENCE.matcher(target).matches()) {
                throw located(templateCode, reader, "include.template 不合法");
            }
        }
        if ("page".equals(element)) {
            String size = attributes.get("size");
            if (size != null && !Set.of("A4", "LETTER").contains(size.toUpperCase(Locale.ROOT))) {
                throw located(templateCode, reader, "页面尺寸不受支持");
            }
            String orientation = attributes.get("orientation");
            if (orientation != null
                    && !Set.of("portrait", "landscape").contains(orientation.toLowerCase(Locale.ROOT))) {
                throw located(templateCode, reader, "页面方向不受支持");
            }
            String margin = attributes.get("margin");
            if (margin != null && !MILLIMETERS.matcher(margin.toLowerCase(Locale.ROOT)).matches()) {
                throw located(templateCode, reader, "页面边距必须使用非负 mm 单位");
            }
            if (margin != null) {
                int marginMicrometers = new BigDecimal(
                        margin.substring(0, margin.length() - 2))
                        .movePointRight(3)
                        .intValueExact();
                boolean landscape = "landscape".equalsIgnoreCase(
                        attributes.getOrDefault("orientation", "portrait"));
                int width = "LETTER".equalsIgnoreCase(attributes.getOrDefault("size", "A4"))
                        ? 215_900 : 210_000;
                int height = "LETTER".equalsIgnoreCase(attributes.getOrDefault("size", "A4"))
                        ? 279_400 : 297_000;
                int effectiveWidth = landscape ? height : width;
                int effectiveHeight = landscape ? width : height;
                if ((long) marginMicrometers * 2 >= effectiveWidth
                        || (long) marginMicrometers * 2 >= effectiveHeight) {
                    throw located(templateCode, reader, "页面边距之和必须小于页面边长");
                }
            }
        }
        if ("heading".equals(element)) {
            String level = attributes.getOrDefault("level", "1");
            if (!Set.of("1", "2", "3", "4", "5", "6").contains(level)) {
                throw located(templateCode, reader, "heading.level 必须在 1 到 6 之间");
            }
        }
        if ("cell".equals(element)) {
            validatePositiveSpan(templateCode, reader, attributes, "row-span");
            validatePositiveSpan(templateCode, reader, attributes, "col-span");
        }
        if ("image".equals(element)) {
            boolean staticResource = attributes.containsKey("resource-id");
            boolean dynamicResource = attributes.containsKey("resource-path");
            if (staticResource == dynamicResource) {
                throw located(templateCode, reader,
                        "image 必须且只能声明一个 resource-id 或 resource-path");
            }
            if (staticResource && attributes.get("resource-id").isBlank()) {
                throw located(templateCode, reader, "image.resource-id 不能为空白");
            }
            if (!attributes.containsKey("alt")) {
                throw located(templateCode, reader, "image.alt 必填");
            }
            validatePositiveMillimeters(templateCode, reader, attributes, "width");
            validatePositiveMillimeters(templateCode, reader, attributes, "height");
        }
        if ("bookmark".equals(element)) {
            if (!attributes.containsKey("id") || attributes.get("label") == null
                    || attributes.get("label").isBlank()) {
                throw located(templateCode, reader, "bookmark.id 和非空 label 必填");
            }
        }
        if ("link".equals(element)) {
            String target = attributes.get("target");
            if (target == null || !NODE_ID.matcher(target).matches()) {
                throw located(templateCode, reader, "link.target 不合法");
            }
        }
        if ("annotation".equals(element)) {
            validateAnnotation(templateCode, reader, attributes);
        }
        if ("format-option".equals(element)) {
            String name = attributes.get("name");
            if (name == null || !FORMAT_OPTION_NAME.matcher(name).matches()) {
                throw located(templateCode, reader, "format-option.name 不合法");
            }
            if (!attributes.containsKey("value") || attributes.get("value").isEmpty()) {
                throw located(templateCode, reader, "format-option.value 不能为空");
            }
        }
    }

    /** 校验批注类型、目标和物理几何都来自受控集合。 */
    private void validateAnnotation(
            String templateCode,
            XMLStreamReader reader,
            Map<String, String> attributes) {
        String type = attributes.get("type");
        if (type == null || !Set.of("text-note", "free-text").contains(type.toLowerCase(Locale.ROOT))) {
            throw located(templateCode, reader, "annotation.type 不受支持");
        }
        String target = attributes.get("target");
        if (target == null || !NODE_ID.matcher(target).matches()) {
            throw located(templateCode, reader, "annotation.target 不合法");
        }
        String placement = attributes.getOrDefault("placement", "top-right").toLowerCase(Locale.ROOT);
        if (!Set.of("top-left", "top-right", "bottom-left", "bottom-right").contains(placement)) {
            throw located(templateCode, reader, "annotation.placement 不受支持");
        }
        validateAnnotationSize(templateCode, reader, attributes, "width");
        validateAnnotationSize(templateCode, reader, attributes, "height");
        validateAnnotationOffset(templateCode, reader, attributes, "offset-x");
        validateAnnotationOffset(templateCode, reader, attributes, "offset-y");
    }

    /** 校验可选批注尺寸可以精确转换且不超过 500 mm。 */
    private void validateAnnotationSize(
            String templateCode,
            XMLStreamReader reader,
            Map<String, String> attributes,
            String name) {
        String value = attributes.get(name);
        if (value == null) {
            return;
        }
        if (!MILLIMETERS.matcher(value.toLowerCase(Locale.ROOT)).matches()) {
            throw located(templateCode, reader, "annotation." + name + " 必须使用正 mm 单位");
        }
        int micrometers = micrometers(templateCode, reader, value, "annotation." + name);
        if (micrometers < 1 || micrometers > 500_000) {
            throw located(templateCode, reader, "annotation." + name + " 超出支持范围");
        }
    }

    /** 校验可选批注偏移可以精确转换且不超过正负 2,000 mm。 */
    private void validateAnnotationOffset(
            String templateCode,
            XMLStreamReader reader,
            Map<String, String> attributes,
            String name) {
        String value = attributes.get(name);
        if (value == null) {
            return;
        }
        if (!SIGNED_MILLIMETERS.matcher(value.toLowerCase(Locale.ROOT)).matches()) {
            throw located(templateCode, reader, "annotation." + name + " 必须使用 mm 单位");
        }
        int micrometers = micrometers(templateCode, reader, value, "annotation." + name);
        if (Math.abs((long) micrometers) > 2_000_000) {
            throw located(templateCode, reader, "annotation." + name + " 超出支持范围");
        }
    }

    /** 把已经通过格式检查的毫米值精确转换为微米。 */
    private int micrometers(
            String templateCode,
            XMLStreamReader reader,
            String value,
            String name) {
        try {
            return new BigDecimal(value.substring(0, value.length() - 2))
                    .movePointRight(3)
                    .intValueExact();
        } catch (ArithmeticException exception) {
            throw located(templateCode, reader, name + " 超出支持范围");
        }
    }

    /** 校验文档元数据的空白和长度边界。 */
    private void validateOptionalText(
            String templateCode,
            XMLStreamReader reader,
            Map<String, String> attributes,
            String name,
            int maxLength) {
        String value = attributes.get(name);
        if (value != null && (value.isBlank() || value.length() > maxLength)) {
            throw located(templateCode, reader, name + " 不能为空白且不能超过 " + maxLength + " 个字符");
        }
    }

    /** 拒绝 XML 解码后才显现的脚本或表达式标记。 */
    private void rejectDecodedExpression(
            String templateCode,
            XMLStreamReader reader,
            String value) {
        if (EXECUTABLE_EXPRESSION.matcher(value).find()) {
            throw located(templateCode, reader, "模板包含禁止的可执行表达式标记");
        }
    }

    /** 校验根结构和 XML 声明版本。 */
    private void validateDocument(PrintTemplate template, CompiledXmlNode root) {
        if (!"document".equals(root.name())) {
            throw nodeLocated(template.templateCode(), root, "根标签必须为 document");
        }
        if (root.children().size() != 1 || !"page".equals(root.children().get(0).name())) {
            throw nodeLocated(template.templateCode(), root, "document 必须且只能包含一个 page");
        }
        String declaredVersion = root.attributes().get("context-version");
        if (declaredVersion == null || !declaredVersion.equals(Integer.toString(template.contextVersion()))) {
            throw nodeLocated(template.templateCode(), root, "context-version 与模板快照不一致");
        }
    }

    /** 把动态属性编译为不可执行的包内描述。 */
    private CompiledXmlNode compileDynamicTree(
            String templateCode,
            CompiledXmlNode node,
            String parentPath,
            Set<String> variables,
            BindingDomain domain) {
        if (domain == BindingDomain.TABLE_ROWS
                && !Set.of("row", "if", "for-each").contains(node.name())) {
            throw nodeLocated(templateCode, node, "表格动态结构只能产生 row");
        }
        if (domain == BindingDomain.BLOCKS && "row".equals(node.name())) {
            throw nodeLocated(templateCode, node, "row 只能出现在表格 header 或 body 中");
        }
        if ("include".equals(node.name())) {
            if (node.includedFragment() == null) {
                throw nodeLocated(templateCode, node, "include 必须通过模板集合编译器解析");
            }
            // include 只保留已编译片段，绑定阶段不会再访问模板仓库。
            return new CompiledXmlNode(
                    node.name(), node.attributes(), List.of(), "", node.line(), node.column(),
                    parentPath + "/include", null, null, null, null, null, null,
                    node.includedFragment());
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
            childVariables = Set.copyOf(nested);
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
                tagPlan = new CompiledTagPlan(
                        customTag.placement(), customTag.contentModel(), plan, variables.isEmpty());
            } catch (RuntimeException exception) {
                throw nodeLocated(templateCode, node, "自定义标签编译失败");
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
            if (children.isEmpty()) {
                throw nodeLocated(templateCode, node, "if 至少包含一个块节点");
            }
            if (node.attributes().containsKey("expression-language")
                    || node.attributes().containsKey("test")) {
                expressionPlan = compileExpressionPlan(templateCode, node, tagPath);
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
            String templateCode, CompiledXmlNode node, String tagPath) {
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
            return plan;
        } catch (RuntimeException exception) {
            throw nodeLocated(templateCode, node, "表达式提供方编译失败");
        }
    }

    /** 校验片段根结构，片段只能提供非空块节点。 */
    private void validateFragment(PrintTemplate template, CompiledXmlNode root) {
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
        if ("if".equals(nodeName) || "for-each".equals(nodeName)) {
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
            throw nodeLocated(templateCode, field, "格式化器配置无效");
        }
    }

    /** 校验表格跨度为核心模型支持范围内的正整数。 */
    private void validatePositiveSpan(
            String templateCode, XMLStreamReader reader,
            Map<String, String> attributes, String name) {
        String value = attributes.get(name);
        if (value == null) {
            return;
        }
        try {
            int span = Integer.parseInt(value);
            if (span < 1 || span > 1_000) {
                throw new NumberFormatException("out of range");
            }
        } catch (NumberFormatException exception) {
            throw located(templateCode, reader, "cell." + name + " 必须在 1 到 1000 之间");
        }
    }

    /** 校验正毫米尺寸且保证能够精确转换为微米整数。 */
    private void validatePositiveMillimeters(
            String templateCode, XMLStreamReader reader,
            Map<String, String> attributes, String name) {
        String value = attributes.get(name);
        if (value == null || !MILLIMETERS.matcher(value.toLowerCase(Locale.ROOT)).matches()) {
            throw located(templateCode, reader, "image." + name + " 必须使用正 mm 单位");
        }
        try {
            int micrometers = new BigDecimal(value.substring(0, value.length() - 2))
                    .movePointRight(3).intValueExact();
            if (micrometers < 1 || micrometers > 2_000_000) {
                throw new ArithmeticException("out of range");
            }
        } catch (ArithmeticException exception) {
            throw located(templateCode, reader, "image." + name + " 超出支持范围");
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

    /** 创建包含安全行列位置的异常。 */
    private PrintCompilationException located(
            String templateCode,
            XMLStreamReader reader,
            String detail) {
        int line = Math.max(reader.getLocation().getLineNumber(), 1);
        int column = Math.max(reader.getLocation().getColumnNumber(), 1);
        return PrintCompilationException.invalid(
                templateCode + "：第 " + line + " 行，第 " + column + " 列：" + detail);
    }

    /** 配置无法静默降级的安全属性。 */
    private void setRequiredProperty(XMLInputFactory factory, String name, Object value, String templateCode) {
        try {
            factory.setProperty(name, value);
            if (!value.equals(factory.getProperty(name))) {
                throw PrintCompilationException.invalid(templateCode + "：XML 解析器未接受安全属性");
            }
        } catch (IllegalArgumentException exception) {
            throw PrintCompilationException.invalid(templateCode + "：XML 解析器不支持必要安全属性", exception);
        }
    }

    /** 在解析期间构建一个节点，并在结束标签处冻结。 */
    private static final class NodeBuilder {

        /** 当前标签名。 */
        private final String name;

        /** 已校验属性。 */
        private final Map<String, String> attributes;

        /** 保持内容顺序的子节点。 */
        private final List<CompiledXmlNode> children = new ArrayList<>();

        /** 当前连续文本片段。 */
        private final StringBuilder pendingText = new StringBuilder();

        /** 当前标签起始行。 */
        private final int line;

        /** 当前标签起始列。 */
        private final int column;

        /** 当前自定义标签是否允许直接行内文本。 */
        private final boolean customTextContainer;

        /** 当前自定义标签是否声明为空内容模型。 */
        private final boolean customEmpty;

        /** 创建节点构建器。 */
        private NodeBuilder(
                String name, Map<String, String> attributes, int line, int column,
                boolean customTextContainer, boolean customEmpty) {
            this.name = name;
            this.attributes = attributes;
            this.line = line;
            this.column = column;
            this.customTextContainer = customTextContainer;
            this.customEmpty = customEmpty;
        }

        /** @return 当前标签名 */
        private String name() {
            return name;
        }

        /** 追加解析器产生的连续文本。 */
        private void appendText(String value, String templateCode, XMLStreamReader reader) {
            if ((long) pendingText.length() + value.length() > XmlDsl.MAX_TEXT_CHARACTERS) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                                + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                                + " 列：文本长度超过 " + XmlDsl.MAX_TEXT_CHARACTERS);
            }
            pendingText.append(value);
        }

        /** 在子元素边界冻结连续文本，从而保留混排顺序。 */
        private void flushText(String templateCode, XMLStreamReader reader) {
            if (pendingText.isEmpty()) {
                return;
            }
            String value = pendingText.toString();
            pendingText.setLength(0);
            if (!TEXT_CONTAINERS.contains(name) && !customTextContainer) {
                if (!value.isBlank()) {
                    throw PrintCompilationException.invalid(
                            templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                                    + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                                    + " 列：标签 " + name + " 不允许直接文本");
                }
                return;
            }
            if (EXECUTABLE_EXPRESSION.matcher(value).find()) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                                + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                                + " 列：模板包含禁止的可执行表达式标记");
            }
            if (!value.isEmpty()) {
                children.add(new CompiledXmlNode("#text", Map.of(), List.of(), value, line, column));
            }
        }

        /** 追加已经冻结的子节点。 */
        private void addChild(CompiledXmlNode child) {
            children.add(child);
        }

        /** 完成空元素和文本标签结构校验。 */
        private CompiledXmlNode build(String templateCode, XMLStreamReader reader) {
            if (("page-break".equals(name) || "format-option".equals(name)
                    || "include".equals(name)
                    || "image".equals(name) || "bookmark".equals(name))
                    && !children.isEmpty()) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                                + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                                + " 列：" + name + " 必须为空元素");
            }
            if ("section".equals(name) && children.isEmpty()) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                                + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                                + " 列：section 至少包含一个块节点");
            }
            if (customEmpty && !children.isEmpty()) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + line + " 行，第 " + column
                                + " 列：自定义空标签不能包含子节点");
            }
            if ("table".equals(name)) {
                validateTableStructure(templateCode);
            }
            if ("row".equals(name) && children.isEmpty()) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + line + " 行，第 " + column
                                + " 列：row 至少包含一个 cell");
            }
            if ("link".equals(name) && children.isEmpty()) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + line + " 行，第 " + column
                                + " 列：link 标签不能为空");
            }
            if ("annotation".equals(name)
                    && children.stream().noneMatch(child -> "field".equals(child.name()))
                    && children.stream()
                    .filter(child -> "#text".equals(child.name()) || "text".equals(child.name()))
                    .allMatch(child -> child.text().isBlank())) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + line + " 行，第 " + column
                                + " 列：annotation 内容不能为空");
            }
            if ("heading".equals(name)
                    && children.stream().noneMatch(child -> Set.of(
                            "field", "bookmark", "link").contains(child.name()))
                    && children.stream()
                    .filter(child -> "#text".equals(child.name()) || "text".equals(child.name()))
                    .allMatch(child -> child.text().isBlank())) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                                + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                                + " 列：heading 内容不能为空");
            }
            String text = "";
            if ("text".equals(name)) {
                StringBuilder content = new StringBuilder();
                for (CompiledXmlNode child : children) {
                    if (!"#text".equals(child.name())) {
                        throw PrintCompilationException.invalid(templateCode + "：text 只能包含文本");
                    }
                    content.append(child.text());
                }
                text = content.toString();
                return new CompiledXmlNode(name, attributes, List.of(), text, line, column);
            }
            return new CompiledXmlNode(name, attributes, children, text, line, column);
        }

        /** 校验表头可选、表体必需且顺序固定。 */
        private void validateTableStructure(String templateCode) {
            int bodyCount = 0;
            boolean bodySeen = false;
            long headerCount = 0;
            for (CompiledXmlNode child : children) {
                if ("body".equals(child.name())) {
                    bodyCount++;
                    bodySeen = true;
                } else if ("header".equals(child.name())) {
                    headerCount++;
                    if (bodySeen) {
                        throw PrintCompilationException.invalid(
                                templateCode + "：第 " + line + " 行，第 " + column
                                        + " 列：table 的 header 顺序必须位于 body 之前");
                    }
                }
            }
            if (bodyCount != 1 || headerCount > 1) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + line + " 行，第 " + column
                                + " 列：table 必须包含一个 body，且至多包含一个 header");
            }
        }
    }

    /** 动态结构在当前位置允许生成的节点类型。 */
    private enum BindingDomain {
        BLOCKS,
        TABLE_ROWS
    }
}
