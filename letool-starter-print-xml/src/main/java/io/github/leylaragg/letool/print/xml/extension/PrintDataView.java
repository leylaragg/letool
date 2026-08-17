package io.github.leylaragg.letool.print.xml.extension;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 提供给可信打印扩展的只读 JSON 数据视图。
 *
 * <p>根数据、循环变量和所有返回值都执行防御性复制，扩展无法修改框架持有的绑定数据。
 * 数据边界只接受标准 JSON 节点，避免 {@code POJONode} 暴露其内部业务对象。</p>
 *
 * @author leyland
 */
public final class PrintDataView {

    /** 与调用方隔离的根 JSON 快照。 */
    private final JsonNode root;

    /** 保持词法作用域顺序的循环变量快照。 */
    private final Map<String, JsonNode> variables;

    /**
     * 创建已完成安全复制的数据视图。
     *
     * @param root 已隔离的标准 JSON 根节点
     * @param variables 已隔离且不可变的变量快照
     */
    private PrintDataView(JsonNode root, Map<String, JsonNode> variables) {
        this.root = root;
        this.variables = variables;
    }

    /**
     * 创建只读数据视图。
     *
     * @param root JSON 对象根节点
     * @param variables 当前可见循环变量
     * @return 与输入节点隔离的数据视图
     */
    public static PrintDataView of(
            JsonNode root, Map<String, ? extends JsonNode> variables) {
        Objects.requireNonNull(root, "root 不能为空");
        Objects.requireNonNull(variables, "variables 不能为空");
        if (!root.isObject()) {
            throw new IllegalArgumentException("root 必须为 JSON 对象");
        }
        JsonNode rootSnapshot = copyStandardJson(root);
        Map<String, JsonNode> variableSnapshots = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends JsonNode> entry : variables.entrySet()) {
            validateVariable(entry.getKey(), entry.getValue());
            variableSnapshots.put(entry.getKey(), copyStandardJson(entry.getValue()));
        }
        return new PrintDataView(
                rootSnapshot, Collections.unmodifiableMap(variableSnapshots));
    }

    /**
     * 基于当前快照派生一个包含新词法变量的数据视图。
     *
     * <p>根快照和既有变量仅在数据视图内部共享；对外读取仍始终返回独立副本。</p>
     *
     * @param name 新变量名称
     * @param value 新变量值
     * @return 不修改当前视图的子数据视图
     */
    public PrintDataView withVariable(String name, JsonNode value) {
        validateVariable(name, value);
        Map<String, JsonNode> childVariables = new LinkedHashMap<>(variables);
        childVariables.put(name, copyStandardJson(value));
        return new PrintDataView(
                root, Collections.unmodifiableMap(childVariables));
    }

    /** 校验词法变量名称和值。 */
    private static void validateVariable(String name, JsonNode value) {
        if (name == null || name.isBlank() || value == null) {
            throw new IllegalArgumentException("变量名称和值不能为空");
        }
    }

    /**
     * 校验整棵树仅包含标准 JSON 类型，并返回独立快照。
     *
     * <p>使用显式栈避免调用方构造的深层 JSON 消耗 Java 调用栈。</p>
     */
    private static JsonNode copyStandardJson(JsonNode source) {
        Deque<JsonNode> pending = new ArrayDeque<>();
        pending.push(source);
        while (!pending.isEmpty()) {
            JsonNode node = pending.pop();
            if (node.isObject()) {
                node.elements().forEachRemaining(pending::push);
            } else if (node.isArray()) {
                node.elements().forEachRemaining(pending::push);
            } else if (!node.isTextual() && !node.isNumber()
                    && !node.isBoolean() && !node.isNull()) {
                throw new IllegalArgumentException("数据视图只允许标准 JSON 节点");
            }
        }
        return source.deepCopy();
    }

    /**
     * 返回根数据的独立副本。
     *
     * @return 可安全读取或修改的 JSON 根节点副本
     */
    public JsonNode root() {
        return root.deepCopy();
    }

    /**
     * 查找当前可见循环变量。
     *
     * @param name 变量名
     * @return 变量值的独立副本，变量不存在时为空
     */
    public Optional<JsonNode> variable(String name) {
        JsonNode value = variables.get(name);
        return value == null ? Optional.empty() : Optional.of(value.deepCopy());
    }

    /**
     * 返回不可修改的变量名视图。
     *
     * @return 保持词法作用域顺序的变量名
     */
    public Set<String> variableNames() {
        return variables.keySet();
    }
}
