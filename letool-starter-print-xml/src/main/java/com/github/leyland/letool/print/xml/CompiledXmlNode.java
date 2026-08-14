package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.xml.expression.PrintExpressionPlan;
import com.github.leyland.letool.print.xml.format.PrintFormatPlan;

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

    /** 当前节点的安全 XML 标签路径。 */
    private final String tagPath;

    /** 当前动态节点使用的可选受限数据路径。 */
    private final CompiledDataPath dataPath;

    /** 当前条件节点的可选结构化条件。 */
    private final CompiledCondition condition;

    /** 循环节点声明的可选变量名。 */
    private final String variableName;

    /** 字段节点可选的已编译格式化计划。 */
    private final PrintFormatPlan formatPlan;

    /** 条件节点可选的扩展表达式计划。 */
    private final PrintExpressionPlan expressionPlan;

    /** 自定义标签可选的已编译绑定计划。 */
    private final CompiledTagPlan tagPlan;

    /** 创建不可变编译节点。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column) {
        this(name, attributes, children, text, line, column, "", null);
    }

    /** 创建包含动态编译描述的不可变节点。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath) {
        this(name, attributes, children, text, line, column, tagPath, dataPath, null);
    }

    /** 创建包含全部动态编译描述的不可变节点。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition) {
        this(name, attributes, children, text, line, column,
                tagPath, dataPath, condition, null, null, null, null);
    }

    /** 创建包含循环描述的不可变节点。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition,
                    String variableName) {
        this(name, attributes, children, text, line, column,
                tagPath, dataPath, condition, variableName, null, null, null);
    }

    /** 创建包含循环描述和格式化计划的不可变节点。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition,
                    String variableName, PrintFormatPlan formatPlan) {
        this(name, attributes, children, text, line, column, tagPath, dataPath,
                condition, variableName, formatPlan, null, null);
    }

    /** 创建包含所有可选编译计划的不可变节点。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition,
                    String variableName, PrintFormatPlan formatPlan,
                    PrintExpressionPlan expressionPlan, CompiledTagPlan tagPlan) {
        this.name = name;
        this.attributes = Map.copyOf(attributes);
        this.children = List.copyOf(children);
        this.text = text;
        this.line = line;
        this.column = column;
        this.tagPath = tagPath;
        this.dataPath = dataPath;
        this.condition = condition;
        this.variableName = variableName;
        this.formatPlan = formatPlan;
        this.expressionPlan = expressionPlan;
        this.tagPlan = tagPlan;
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

    /** @return 安全 XML 标签路径 */
    String tagPath() {
        return tagPath;
    }

    /** @return 可选受限数据路径 */
    CompiledDataPath dataPath() {
        return dataPath;
    }

    /** @return 可选结构化条件 */
    CompiledCondition condition() {
        return condition;
    }

    /** @return 可选循环变量名 */
    String variableName() {
        return variableName;
    }

    /** @return 字段节点可选的已编译格式化计划 */
    PrintFormatPlan formatPlan() {
        return formatPlan;
    }

    /** @return 条件节点可选的扩展表达式计划 */
    PrintExpressionPlan expressionPlan() {
        return expressionPlan;
    }

    /** @return 自定义标签可选的绑定计划 */
    CompiledTagPlan tagPlan() {
        return tagPlan;
    }
}
