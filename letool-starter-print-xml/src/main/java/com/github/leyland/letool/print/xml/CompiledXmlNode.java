package com.github.leyland.letool.print.xml;

import java.util.List;
import java.util.Map;

/**
 * 不包含解析器对象的不可变 XML DSL 节点。
 *
 * @author leyland
 */
final class CompiledXmlNode {

    /** DSL 标签名。 */
    private final String name;

    /** 经过白名单校验的属性。 */
    private final Map<String, String> attributes;

    /** 保持模板顺序的子节点。 */
    private final List<CompiledXmlNode> children;

    /** 文本节点内容；其他节点为空字符串。 */
    private final String text;

    /** 节点起始标签所在行。 */
    private final int line;

    /** 节点起始标签所在列。 */
    private final int column;

    /** 创建不可变编译节点。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column) {
        this.name = name;
        this.attributes = Map.copyOf(attributes);
        this.children = List.copyOf(children);
        this.text = text;
        this.line = line;
        this.column = column;
    }

    /** @return DSL 标签名 */
    String name() {
        return name;
    }

    /** @return 不可变属性 */
    Map<String, String> attributes() {
        return attributes;
    }

    /** @return 不可变子节点 */
    List<CompiledXmlNode> children() {
        return children;
    }

    /** @return 文本节点内容 */
    String text() {
        return text;
    }

    /** @return 节点起始标签所在行 */
    int line() {
        return line;
    }

    /** @return 节点起始标签所在列 */
    int column() {
        return column;
    }
}
