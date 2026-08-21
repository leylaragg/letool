package io.github.leylaragg.letool.print.xml;

import java.util.List;
import java.util.Map;

/**
 * XML 源完成安全读取后的不可变节点，不携带任何绑定期计划。
 *
 * @author leyland
 */
final class ParsedXmlNode {

    /** DSL 标签名。 */
    private final String name;

    /** 已通过语法白名单检查的属性。 */
    private final Map<String, String> attributes;

    /** 保持源码顺序的子节点。 */
    private final List<ParsedXmlNode> children;

    /** text 标签或行内文本节点的内容。 */
    private final String text;

    /** 节点开始位置所在行。 */
    private final int line;

    /** 节点开始位置所在列。 */
    private final int column;

    /** 保存一个已经通过源码解析和基础语法检查的节点。 */
    ParsedXmlNode(String name, Map<String, String> attributes,
                  List<ParsedXmlNode> children, String text, int line, int column) {
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

    /** @return 保持源码顺序的子节点 */
    List<ParsedXmlNode> children() {
        return children;
    }

    /** @return 文本内容 */
    String text() {
        return text;
    }

    /** @return 节点开始位置所在行 */
    int line() {
        return line;
    }

    /** @return 节点开始位置所在列 */
    int column() {
        return column;
    }
}
