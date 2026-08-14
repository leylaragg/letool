package com.github.leyland.letool.print.spel;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.leyland.letool.print.xml.extension.PrintDataView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 封装打印数据视图的内部只读 JSON 节点。
 *
 * <p>该类型不会向 SpEL 暴露 {@link JsonNode}、业务 POJO 或可修改集合，只提供属性和数组读取器需要的最小包级操作。
 * 节点及其预算由单次求值独占，不跨表达式或线程共享。</p>
 *
 * @author leyland
 */
final class RestrictedSpelDataNode {

    /** 当前包装的标准 JSON 节点防御性副本。 */
    private final JsonNode node;

    /** 仅根节点持有的词法循环变量快照。 */
    private final Map<String, JsonNode> variables;

    /** 当前节点是否为表达式读取入口。 */
    private final boolean root;

    /** 当前单次求值共享的资源预算。 */
    private final RestrictedSpelBudget budget;

    /**
     * 创建内部只读数据节点。
     *
     * @param node 当前标准 JSON 节点
     * @param variables 根作用域变量快照
     * @param root 是否为读取入口
     * @param budget 当前单次求值预算
     * @throws NullPointerException 节点、变量快照或预算为空时抛出
     */
    private RestrictedSpelDataNode(
            JsonNode node, Map<String, JsonNode> variables, boolean root,
            RestrictedSpelBudget budget) {
        this.node = Objects.requireNonNull(node, "node 不能为空");
        this.variables = Objects.requireNonNull(variables, "variables 不能为空");
        this.root = root;
        this.budget = Objects.requireNonNull(budget, "budget 不能为空");
    }

    /**
     * 从框架防御性数据视图创建一次求值使用的根节点。
     *
     * @param data 当前只读打印数据
     * @param budget 当前单次求值预算
     * @return 独立的表达式数据根
     * @throws NullPointerException 数据视图或预算为空时抛出
     */
    static RestrictedSpelDataNode from(
            PrintDataView data, RestrictedSpelBudget budget) {
        Objects.requireNonNull(data, "data 不能为空");
        Objects.requireNonNull(budget, "budget 不能为空");
        Map<String, JsonNode> variables = new LinkedHashMap<>();
        for (String name : data.variableNames()) {
            // PrintDataView 已执行标准 JSON 校验；这里再次取得副本以隔离单次求值。
            data.variable(name).ifPresent(value -> variables.put(name, value));
        }
        return new RestrictedSpelDataNode(
                data.root(), Map.copyOf(variables), true, budget);
    }

    /**
     * 判断当前节点是否能读取指定属性。
     *
     * @param name 属性或顶层循环变量名称
     * @return 属性是否存在
     */
    boolean hasProperty(String name) {
        if (root && variables.containsKey(name)) {
            return true;
        }
        return node.isObject() && node.has(name);
    }

    /**
     * 读取对象属性或顶层循环变量。
     *
     * <p>循环变量优先于同名根属性，保持 XML 绑定器已经建立的词法遮蔽语义。</p>
     *
     * @param name 属性或变量名称
     * @return 不暴露 JSON 实现的 SpEL 值
     * @throws IllegalArgumentException 属性不存在或访问预算超限时抛出
     */
    Object readProperty(String name) {
        // 每次真实数据读取都计入当前求值预算，嵌套节点沿用同一预算实例。
        budget.checkpoint();
        JsonNode value;
        if (root && variables.containsKey(name)) {
            value = variables.get(name);
        } else {
            value = node.get(name);
        }
        if (value == null) {
            throw new IllegalArgumentException("属性不存在");
        }
        return convert(value);
    }

    /**
     * 判断当前节点是否能读取指定数组下标。
     *
     * @param index 待读取下标对象
     * @return 下标为有效非负整数且没有越界时返回 {@code true}
     */
    boolean hasIndex(Object index) {
        return node.isArray() && index instanceof Integer integer
                && integer >= 0 && integer < node.size();
    }

    /**
     * 读取当前数组节点的指定下标。
     *
     * @param index 已校验的非负整数下标
     * @return 不暴露 JSON 实现的 SpEL 值
     * @throws IllegalArgumentException 下标无效或访问预算超限时抛出
     */
    Object readIndex(Object index) {
        // 下标访问与属性访问使用同一累计预算，避免交替访问绕过单项限制。
        budget.checkpoint();
        if (!hasIndex(index)) {
            throw new IllegalArgumentException("数组下标不合法");
        }
        return convert(node.get((Integer) index));
    }

    /**
     * 将标准 JSON 节点转换为最小只读 SpEL 值。
     *
     * @param value 待转换标准 JSON 节点
     * @return JDK 标量、{@code null} 或新的内部容器节点
     * @throws IllegalArgumentException 节点不是框架允许的标准 JSON 类型时抛出
     */
    private Object convert(JsonNode value) {
        if (value.isObject() || value.isArray()) {
            // 容器必须继续保持内部包装，防止 JsonNode、Map、List 或业务 POJO 进入 SpEL 对象图。
            return new RestrictedSpelDataNode(
                    value, Map.of(), false, budget);
        }
        if (value.isNull()) {
            return null;
        }
        // 只有不可变或值语义明确的标准 JSON 标量才转换为 JDK 值，供白名单运算节点比较。
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        throw new IllegalArgumentException("只允许标准 JSON 数据");
    }
}
