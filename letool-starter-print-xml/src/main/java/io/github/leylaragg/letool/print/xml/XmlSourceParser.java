package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.document.PageSize;
import io.github.leylaragg.letool.print.template.TemplateDefinition;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 使用受限 StAX 配置读取 XML 源，并保留后续编译需要的位置与文本顺序。
 *
 * @author leyland
 */
final class XmlSourceParser {

    /** 解析器建立前直接拒绝的 XML 外部访问和声明标记。 */
    private static final Pattern XML_SECURITY_MARKER = Pattern.compile(
            "(?is)<!--.*?-->|<!\\[CDATA\\[.*?]]>|<!DOCTYPE|<!ENTITY|<\\?(?!xml\\s)");

    /** 受控 DSL 不允许出现的脚本或任意表达式标记。 */
    private static final Pattern EXECUTABLE_EXPRESSION = Pattern.compile(
            "(?is)<%|%>|\\$\\{|#\\{|javascript\\s*:|groovy\\s*:");

    /** 文档节点逻辑 ID 的稳定安全格式。 */
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    /** 批注宽高最多为 500 mm。 */
    private static final int MAX_ANNOTATION_SIZE_MICROMETERS = 500_000;

    /** 批注偏移绝对值最多为 2,000 mm。 */
    private static final int MAX_ANNOTATION_OFFSET_MICROMETERS = 2_000_000;

    /** 图片宽高最多为 2,000 mm。 */
    private static final int MAX_IMAGE_SIZE_MICROMETERS = 2_000_000;

    /** 单元格行列跨度最多为 1,000。 */
    private static final int MAX_TABLE_SPAN = 1_000;

    /** 循环和片段参数名的稳定安全格式。 */
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    /** 格式选项名称的稳定安全格式。 */
    private static final Pattern FORMAT_OPTION_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    /** include 引用使用与模板代码兼容的稳定格式。 */
    private static final Pattern TEMPLATE_REFERENCE = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    /** 拒绝任何外部实体或资源解析请求。 */
    private static final XMLResolver REJECTING_RESOLVER =
            (publicId, systemId, baseUri, namespace) -> {
                throw new XMLStreamException("外部 XML 资源访问被禁止");
            };

    /** 当前编译器启用的 XML 语法。 */
    private final XmlGrammar grammar;

    /** 使用同一份标签语法读取源码。 */
    XmlSourceParser(XmlGrammar grammar) {
        this.grammar = grammar;
    }

    /** 解码并安全解析一个 XML 模板定义。 */
    ParsedXmlTemplate parse(TemplateDefinition definition) {
        PrintTemplate template = definition.template();
        String source = decodeUtf8(template);
        rejectUnsafeSource(template.templateCode(), source);
        return parse(definition, source);
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
                ParsedXmlNode root = null;
                int nodeCount = 0;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamReader.START_ELEMENT) {
                        if (++nodeCount > XmlDsl.MAX_NODE_COUNT) {
                            throw located(templateCode, reader,
                                    "节点数量超过 " + XmlDsl.MAX_NODE_COUNT);
                        }
                        if (stack.size() + 1 > XmlDsl.MAX_NODE_DEPTH) {
                            throw located(templateCode, reader,
                                    "节点深度超过 " + XmlDsl.MAX_NODE_DEPTH);
                        }
                        String parent = stack.isEmpty() ? null : stack.peek().name();
                        String name = grammar.validateStartElement(definition, reader, parent);
                        if (!stack.isEmpty()) {
                            stack.peek().flushText(templateCode, reader);
                        }
                        stack.push(new NodeBuilder(
                                name, readAttributes(templateCode, reader),
                                Math.max(reader.getLocation().getLineNumber(), 1),
                                Math.max(reader.getLocation().getColumnNumber(), 1)));
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
                        ParsedXmlNode node = completed.build(templateCode);
                        grammar.validateCompletedNode(templateCode, node);
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
                return new ParsedXmlTemplate(definition.type(), template, root, nodeCount);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException exception) {
            int line = exception.getLocation() == null
                    ? 1 : Math.max(exception.getLocation().getLineNumber(), 1);
            int column = exception.getLocation() == null
                    ? 1 : Math.max(exception.getLocation().getColumnNumber(), 1);
            throw XmlDiagnosticExceptions.source(
                    templateCode, line, column, "XML 解析失败", exception);
        }
    }

    /** 读取并校验无命名空间的白名单属性。 */
    private Map<String, String> readAttributes(String templateCode, XMLStreamReader reader) {
        String element = reader.getLocalName();
        Set<String> allowed = grammar.allowedAttributes(element);
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            String namespace = reader.getAttributeNamespace(index);
            String name = reader.getAttributeLocalName(index);
            if ((namespace != null && !namespace.isEmpty()) || !allowed.contains(name)) {
                throw located(templateCode, reader,
                        "标签 " + element + " 包含未知属性：" + name);
            }
            String value = reader.getAttributeValue(index);
            rejectDecodedExpression(templateCode, reader, value);
            if (attributes.putIfAbsent(name, value) != null) {
                throw located(templateCode, reader,
                        "标签 " + element + " 包含重复属性：" + name);
            }
        }
        validateStaticAttributes(templateCode, element, attributes, reader);
        return attributes;
    }

    /** 校验不依赖上下文的静态属性，保证发布编译即可发现错误。 */
    private void validateStaticAttributes(
            String templateCode, String element,
            Map<String, String> attributes, XMLStreamReader reader) {
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
        if (Set.of("text-style", "paragraph-style", "table-style", "cell-style").contains(element)) {
            String name = attributes.get("name");
            if (name == null || !name.matches("[a-z][a-z0-9._-]{0,63}")) {
                throw located(templateCode, reader, element + ".name 不合法");
            }
        }
        if ("border".equals(element)) {
            for (String name : Set.of("side", "line", "width", "color")) {
                if (!attributes.containsKey(name)) {
                    throw located(templateCode, reader, "border." + name + " 必填");
                }
            }
        }
        if ("include".equals(element)) {
            String target = attributes.get("template");
            if (target == null || !TEMPLATE_REFERENCE.matcher(target).matches()) {
                throw located(templateCode, reader, "include.template 不合法");
            }
        }
        if ("page".equals(element)) {
            validatePageAttributes(templateCode, reader, attributes);
        }
        if ("heading".equals(element)) {
            String level = attributes.getOrDefault("level", "1");
            if (!Set.of("1", "2", "3", "4", "5", "6").contains(level)) {
                throw located(templateCode, reader, "heading.level 必须在 1 到 6 之间");
            }
        }
        if ("with".equals(element)) {
            String name = attributes.get("name");
            String path = attributes.get("path");
            if (name == null || !VARIABLE_NAME.matcher(name).matches()) {
                throw located(templateCode, reader, "with.name 不合法");
            }
            if (path == null || path.isBlank()) {
                throw located(templateCode, reader, "with.path 不能为空");
            }
        }
        if ("table-of-contents".equals(element)) {
            int minLevel = tableOfContentsLevel(
                    templateCode, reader, attributes.getOrDefault("min-level", "1"), "min-level");
            int maxLevel = tableOfContentsLevel(
                    templateCode, reader, attributes.getOrDefault("max-level", "3"), "max-level");
            if (minLevel > maxLevel) {
                throw located(templateCode, reader,
                        "table-of-contents.min-level 不能大于 max-level");
            }
        }
        if ("cell".equals(element)) {
            validatePositiveSpan(templateCode, reader, attributes, "row-span");
            validatePositiveSpan(templateCode, reader, attributes, "col-span");
        }
        if ("image".equals(element)) {
            validateImage(templateCode, reader, attributes);
        }
        if ("bookmark".equals(element)
                && (!attributes.containsKey("id") || attributes.get("label") == null
                || attributes.get("label").isBlank())) {
            throw located(templateCode, reader, "bookmark.id 和非空 label 必填");
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

    /** 校验页面尺寸、方向和统一边距。 */
    private void validatePageAttributes(
            String templateCode, XMLStreamReader reader, Map<String, String> attributes) {
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
        if (margin == null) {
            return;
        }
        if (!StrictXmlMillimeterValue.isUnsigned(margin)) {
            throw located(templateCode, reader, "页面边距必须使用非负 mm 单位");
        }
        int marginMicrometers = StrictXmlMillimeterValue.toMicrometers(margin);
        boolean landscape = "landscape".equalsIgnoreCase(
                attributes.getOrDefault("orientation", "portrait"));
        PageSize pageSize = "LETTER".equalsIgnoreCase(attributes.getOrDefault("size", "A4"))
                ? PageSize.LETTER : PageSize.A4;
        int width = pageSize.widthMicrometers();
        int height = pageSize.heightMicrometers();
        int effectiveWidth = landscape ? height : width;
        int effectiveHeight = landscape ? width : height;
        if ((long) marginMicrometers * 2 >= effectiveWidth
                || (long) marginMicrometers * 2 >= effectiveHeight) {
            throw located(templateCode, reader, "页面边距之和必须小于页面边长");
        }
    }

    /** 校验图片资源来源和物理尺寸。 */
    private void validateImage(
            String templateCode, XMLStreamReader reader, Map<String, String> attributes) {
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

    /** 校验批注类型、目标和物理几何都来自受控集合。 */
    private void validateAnnotation(
            String templateCode, XMLStreamReader reader, Map<String, String> attributes) {
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
            String templateCode, XMLStreamReader reader,
            Map<String, String> attributes, String name) {
        String value = attributes.get(name);
        if (value == null) {
            return;
        }
        if (!StrictXmlMillimeterValue.isUnsigned(value)) {
            throw located(templateCode, reader, "annotation." + name + " 必须使用正 mm 单位");
        }
        int micrometers = micrometers(templateCode, reader, value, "annotation." + name);
        if (micrometers < 1 || micrometers > MAX_ANNOTATION_SIZE_MICROMETERS) {
            throw located(templateCode, reader, "annotation." + name + " 超出支持范围");
        }
    }

    /** 校验可选批注偏移可以精确转换且不超过正负 2,000 mm。 */
    private void validateAnnotationOffset(
            String templateCode, XMLStreamReader reader,
            Map<String, String> attributes, String name) {
        String value = attributes.get(name);
        if (value == null) {
            return;
        }
        if (!StrictXmlMillimeterValue.isSigned(value)) {
            throw located(templateCode, reader, "annotation." + name + " 必须使用 mm 单位");
        }
        int micrometers = micrometers(templateCode, reader, value, "annotation." + name);
        if (Math.abs((long) micrometers) > MAX_ANNOTATION_OFFSET_MICROMETERS) {
            throw located(templateCode, reader, "annotation." + name + " 超出支持范围");
        }
    }

    /** 把已经通过格式检查的毫米值精确转换为微米。 */
    private int micrometers(
            String templateCode, XMLStreamReader reader, String value, String name) {
        try {
            return StrictXmlMillimeterValue.toMicrometers(value);
        } catch (ArithmeticException exception) {
            throw located(templateCode, reader, name + " 超出支持范围");
        }
    }

    /** 校验文档元数据的空白和长度边界。 */
    private void validateOptionalText(
            String templateCode, XMLStreamReader reader,
            Map<String, String> attributes, String name, int maxLength) {
        String value = attributes.get(name);
        if (value != null && (value.isBlank() || value.length() > maxLength)) {
            throw located(templateCode, reader,
                    name + " 不能为空白且不能超过 " + maxLength + " 个字符");
        }
    }

    /** 目录层级与标题保持同一套 1 到 6 的边界。 */
    private int tableOfContentsLevel(
            String templateCode, XMLStreamReader reader, String value, String name) {
        if (!Set.of("1", "2", "3", "4", "5", "6").contains(value)) {
            throw located(templateCode, reader,
                    "table-of-contents." + name + " 必须在 1 到 6 之间");
        }
        return Integer.parseInt(value);
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
            if (span < 1 || span > MAX_TABLE_SPAN) {
                throw new NumberFormatException("out of range");
            }
        } catch (NumberFormatException exception) {
            throw located(templateCode, reader,
                    "cell." + name + " 必须在 1 到 1000 之间");
        }
    }

    /** 校验正毫米尺寸且保证能够精确转换为微米整数。 */
    private void validatePositiveMillimeters(
            String templateCode, XMLStreamReader reader,
            Map<String, String> attributes, String name) {
        String value = attributes.get(name);
        if (!StrictXmlMillimeterValue.isUnsigned(value)) {
            throw located(templateCode, reader, "image." + name + " 必须使用正 mm 单位");
        }
        try {
            int micrometers = StrictXmlMillimeterValue.toMicrometers(value);
            if (micrometers < 1 || micrometers > MAX_IMAGE_SIZE_MICROMETERS) {
                throw new ArithmeticException("out of range");
            }
        } catch (ArithmeticException exception) {
            throw located(templateCode, reader, "image." + name + " 超出支持范围");
        }
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
            throw PrintCompilationException.invalid(
                    template.templateCode() + "：模板不是合法 UTF-8", exception);
        }
    }

    /** 在 XML 解析前拒绝提供方无关的危险语法。 */
    private void rejectUnsafeSource(String templateCode, String source) {
        java.util.regex.Matcher xmlSecurity = XML_SECURITY_MARKER.matcher(source);
        while (xmlSecurity.find()) {
            String marker = xmlSecurity.group();
            if (!marker.startsWith("<!--") && !marker.startsWith("<![CDATA[")) {
                throw sourceLocated(templateCode, source, xmlSecurity.start(),
                        "模板包含禁止的 XML 声明或外部资源入口");
            }
        }
    }

    /** 拒绝 XML 解码后才显现的脚本或表达式标记。 */
    private void rejectDecodedExpression(
            String templateCode, XMLStreamReader reader, String value) {
        if (EXECUTABLE_EXPRESSION.matcher(value).find()) {
            throw located(templateCode, reader, "模板包含禁止的可执行表达式标记");
        }
    }

    /** 根据源码字符偏移计算不包含正文的安全行列位置。 */
    private PrintCompilationException sourceLocated(
            String templateCode, String source, int offset, String detail) {
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
        return XmlDiagnosticExceptions.source(templateCode, line, column, detail);
    }

    /** 配置无法静默降级的安全属性。 */
    private void setRequiredProperty(
            XMLInputFactory factory, String name, Object value, String templateCode) {
        try {
            factory.setProperty(name, value);
            if (!value.equals(factory.getProperty(name))) {
                throw PrintCompilationException.invalid(templateCode + "：XML 解析器未接受安全属性");
            }
        } catch (IllegalArgumentException exception) {
            throw PrintCompilationException.invalid(
                    templateCode + "：XML 解析器不支持必要安全属性", exception);
        }
    }

    /** 创建只带安全模板代码和当前位置的解析异常。 */
    private PrintCompilationException located(
            String templateCode, XMLStreamReader reader, String detail) {
        return XmlDiagnosticExceptions.source(
                templateCode,
                Math.max(reader.getLocation().getLineNumber(), 1),
                Math.max(reader.getLocation().getColumnNumber(), 1),
                detail);
    }

    /** 在解析期间构建一个节点，并在结束标签处冻结。 */
    private final class NodeBuilder {

        /** 当前标签名。 */
        private final String name;

        /** 已校验属性。 */
        private final Map<String, String> attributes;

        /** 保持内容顺序的子节点。 */
        private final List<ParsedXmlNode> children = new ArrayList<>();

        /** 当前连续文本片段。 */
        private final StringBuilder pendingText = new StringBuilder();

        /** 当前标签起始行。 */
        private final int line;

        /** 当前标签起始列。 */
        private final int column;

        /** 创建节点构建器。 */
        private NodeBuilder(
                String name, Map<String, String> attributes, int line, int column) {
            this.name = name;
            this.attributes = attributes;
            this.line = line;
            this.column = column;
        }

        /** @return 当前标签名 */
        private String name() {
            return name;
        }

        /** 追加解析器产生的连续文本。 */
        private void appendText(String value, String templateCode, XMLStreamReader reader) {
            if ((long) pendingText.length() + value.length() > XmlDsl.MAX_TEXT_CHARACTERS) {
                throw located(templateCode, reader,
                        "文本长度超过 " + XmlDsl.MAX_TEXT_CHARACTERS);
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
            if (!grammar.acceptsDirectText(name)) {
                if (!value.isBlank()) {
                    throw located(templateCode, reader,
                            "标签 " + name + " 不允许直接文本");
                }
                return;
            }
            rejectDecodedExpression(templateCode, reader, value);
            if (!value.isEmpty()) {
                children.add(new ParsedXmlNode(
                        "#text", Map.of(), List.of(), value, line, column));
            }
        }

        /** 追加已经冻结的子节点。 */
        private void addChild(ParsedXmlNode child) {
            children.add(child);
        }

        /** 合并 text 标签的连续文本，并冻结当前节点。 */
        private ParsedXmlNode build(String templateCode) {
            if (!"text".equals(name)) {
                return new ParsedXmlNode(name, attributes, children, "", line, column);
            }
            StringBuilder content = new StringBuilder();
            for (ParsedXmlNode child : children) {
                if (!"#text".equals(child.name())) {
                    throw PrintCompilationException.invalid(templateCode + "：text 只能包含文本");
                }
                content.append(child.text());
            }
            return new ParsedXmlNode(
                    name, attributes, List.of(), content.toString(), line, column);
        }
    }
}
