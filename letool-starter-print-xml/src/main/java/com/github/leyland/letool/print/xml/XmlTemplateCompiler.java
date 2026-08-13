package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;

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
    private static final Map<String, Set<String>> ALLOWED_ATTRIBUTES = Map.of(
            "document", Set.of("context-version", "title", "author", "language"),
            "page", Set.of("size", "orientation", "margin"),
            "section", Set.of("id"),
            "heading", Set.of("id", "level"),
            "paragraph", Set.of("id"),
            "text", Set.of(),
            "page-break", Set.of());

    /** 每个父标签允许包含的直接子标签。 */
    private static final Map<String, Set<String>> ALLOWED_CHILDREN = Map.of(
            "document", Set.of("page"),
            "page", Set.of("section", "heading", "paragraph", "page-break"),
            "section", Set.of("section", "heading", "paragraph", "page-break"),
            "heading", Set.of("text"),
            "paragraph", Set.of("text"),
            "text", Set.of(),
            "page-break", Set.of());

    /** 允许直接保存文本内容的标签。 */
    private static final Set<String> TEXT_CONTAINERS = Set.of("heading", "paragraph", "text");

    /** 文档节点逻辑 ID 的稳定安全格式。 */
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    /** 页面边距只接受最多三位小数的非负毫米值。 */
    private static final Pattern MILLIMETERS = Pattern.compile(
            "(?:0|[1-9][0-9]{0,3})(?:\\.[0-9]{1,3})?mm");

    /**
     * 编译模板快照。
     *
     * @param template Letool XML 模板
     * @return 完成安全和结构校验的编译快照
     * @throws NullPointerException 模板为空时抛出
     */
    public CompiledXmlTemplate compile(PrintTemplate template) {
        Objects.requireNonNull(template, "template 不能为空");
        if (!TemplateFormat.LETOOL_XML.equals(template.templateFormat())) {
            throw PrintCompilationException.invalid(template.templateCode() + "：模板格式不是 letool-xml");
        }
        if (template.dslVersion() != XmlDsl.VERSION) {
            throw PrintCompilationException.invalid(template.templateCode() + "：不支持的 DSL 版本");
        }
        String source = decodeUtf8(template);
        rejectUnsafeSource(template.templateCode(), source);
        CompiledXmlNode root = parse(template, source);
        return new CompiledXmlTemplate(
                template.templateCode(),
                template.dslVersion(),
                template.templateSetVersion(),
                template.contextVersion(),
                root);
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
    private CompiledXmlNode parse(PrintTemplate template, String source) {
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
                        String name = validateStartElement(template, reader, stack);
                        if (!stack.isEmpty()) {
                            stack.peek().flushText(templateCode, reader);
                        }
                        stack.push(new NodeBuilder(
                                name,
                                readAttributes(templateCode, reader),
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
                validateDocument(template, root);
                return root;
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
            PrintTemplate template,
            XMLStreamReader reader,
            Deque<NodeBuilder> stack) {
        String templateCode = template.templateCode();
        if (!XmlDsl.NAMESPACE_V1.equals(reader.getNamespaceURI())) {
            throw located(templateCode, reader, "命名空间不受支持");
        }
        String name = reader.getLocalName();
        if (!ALLOWED_ATTRIBUTES.containsKey(name)) {
            throw located(templateCode, reader, "未知标签：" + name);
        }
        if (stack.isEmpty()) {
            if (!"document".equals(name)) {
                throw located(templateCode, reader, "根标签必须为 document");
            }
            return name;
        }
        String parent = stack.peek().name();
        if (!ALLOWED_CHILDREN.get(parent).contains(name)) {
            throw located(templateCode, reader, parent + " 不能包含 " + name);
        }
        return name;
    }

    /** 读取并校验无命名空间的白名单属性。 */
    private Map<String, String> readAttributes(String templateCode, XMLStreamReader reader) {
        String element = reader.getLocalName();
        Set<String> allowed = ALLOWED_ATTRIBUTES.get(element);
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

        /** 创建节点构建器。 */
        private NodeBuilder(String name, Map<String, String> attributes, int line, int column) {
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
            if (!TEXT_CONTAINERS.contains(name)) {
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
            if ("page-break".equals(name) && !children.isEmpty()) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                                + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                                + " 列：page-break 必须为空元素");
            }
            if ("section".equals(name) && children.isEmpty()) {
                throw PrintCompilationException.invalid(
                        templateCode + "：第 " + Math.max(reader.getLocation().getLineNumber(), 1)
                                + " 行，第 " + Math.max(reader.getLocation().getColumnNumber(), 1)
                                + " 列：section 至少包含一个块节点");
            }
            if ("heading".equals(name) && children.stream()
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
    }
}
