package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.xml.expression.PrintExpressionPlan;
import io.github.leylaragg.letool.print.xml.format.PrintFormatPlan;

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

    /** include 节点指向的已编译片段。 */
    private final CompiledXmlFragment includedFragment;

    /** 保存最基础的 XML 节点信息。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column) {
        this(name, attributes, children, text, line, column, "", null);
    }

    /** 在基础节点上补充标签路径和数据路径。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath) {
        this(name, attributes, children, text, line, column, tagPath, dataPath, null);
    }

    /** 在动态节点上补充结构化条件。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition) {
        this(name, attributes, children, text, line, column,
                tagPath, dataPath, condition, null, null, null, null, null);
    }

    /** 在动态节点上补充循环变量。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition,
                    String variableName) {
        this(name, attributes, children, text, line, column,
                tagPath, dataPath, condition, variableName, null, null, null, null);
    }

    /** 在动态节点上补充字段格式化计划。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition,
                    String variableName, PrintFormatPlan formatPlan) {
        this(name, attributes, children, text, line, column, tagPath, dataPath,
                condition, variableName, formatPlan, null, null, null);
    }

    /** 在动态节点上补齐表达式和自定义标签计划。 */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition,
                    String variableName, PrintFormatPlan formatPlan,
                    PrintExpressionPlan expressionPlan, CompiledTagPlan tagPlan) {
        this(name, attributes, children, text, line, column, tagPath, dataPath,
                condition, variableName, formatPlan, expressionPlan, tagPlan, null);
    }

    /**
     * 保存节点的完整编译信息，include 节点会额外携带目标片段。
     *
     * @param name DSL 标签名
     * @param attributes 已校验属性
     * @param children 子节点
     * @param text 文本内容
     * @param line 起始行
     * @param column 起始列
     * @param tagPath 安全标签路径
     * @param dataPath 受限数据路径
     * @param condition 结构化条件
     * @param variableName 循环变量名
     * @param formatPlan 格式化计划
     * @param expressionPlan 表达式计划
     * @param tagPlan 自定义标签计划
     * @param includedFragment include 指向的片段
     */
    CompiledXmlNode(String name, Map<String, String> attributes,
                    List<CompiledXmlNode> children, String text, int line, int column,
                    String tagPath, CompiledDataPath dataPath, CompiledCondition condition,
                    String variableName, PrintFormatPlan formatPlan,
                    PrintExpressionPlan expressionPlan, CompiledTagPlan tagPlan,
                    CompiledXmlFragment includedFragment) {
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
        this.includedFragment = includedFragment;
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

    /** @return include 节点指向的已编译片段 */
    CompiledXmlFragment includedFragment() {
        return includedFragment;
    }

    /**
     * 保留当前节点信息，只替换解析后的子节点和片段引用。
     *
     * @param resolvedChildren 已解析 include 的子节点
     * @param fragment 当前 include 指向的片段
     * @return 新的不可变编译节点
     */
    CompiledXmlNode withChildrenAndFragment(List<CompiledXmlNode> resolvedChildren,
                                            CompiledXmlFragment fragment) {
        return new CompiledXmlNode(name, attributes, resolvedChildren, text, line, column,
                tagPath, dataPath, condition, variableName, formatPlan,
                expressionPlan, tagPlan, fragment);
    }
}
