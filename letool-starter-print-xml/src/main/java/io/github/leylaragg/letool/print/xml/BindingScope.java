package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leylaragg.letool.print.xml.extension.PrintDataView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次 XML 绑定使用的只读词法数据作用域。
 *
 * @author leyland
 */
final class BindingScope {

    /** 根 JSON 上下文快照。 */
    private final JsonNode root;

    /** 当前可见循环变量。 */
    private final Map<String, JsonNode> variables;

    /** 父词法作用域；根作用域为 {@code null}。 */
    private final BindingScope parent;

    /** 相对父作用域新增的变量名；根作用域为 {@code null}。 */
    private final String boundName;

    /** 相对父作用域新增的变量快照；根作用域为 {@code null}。 */
    private final JsonNode boundValue;

    /** 按需创建并复用的扩展数据视图。 */
    private PrintDataView dataView;

    /** 创建根作用域。 */
    BindingScope(JsonNode root) {
        this(root.deepCopy(), Map.of(), null, null, null);
    }

    /** 创建不可变作用域。 */
    private BindingScope(
            JsonNode root, Map<String, JsonNode> variables, BindingScope parent,
            String boundName, JsonNode boundValue) {
        this.root = root;
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        this.parent = parent;
        this.boundName = boundName;
        this.boundValue = boundValue;
    }

    /**
     * 在当前作用域增加循环变量。
     *
     * @param name 变量名
     * @param value 变量值
     * @return 包含新变量的子作用域
     */
    BindingScope child(String name, JsonNode value) {
        Map<String, JsonNode> childVariables = new LinkedHashMap<>(variables);
        JsonNode valueSnapshot = value.deepCopy();
        childVariables.put(name, valueSnapshot);
        return new BindingScope(root, childVariables, this, name, valueSnapshot);
    }

    /**
     * 为共享片段建立只包含根数据的闭合作用域。
     *
     * @return 不带调用方循环变量的作用域
     */
    BindingScope rootOnly() {
        if (variables.isEmpty()) {
            return this;
        }
        return new BindingScope(root, Map.of(), null, null, null);
    }

    /**
     * 从根上下文建立只包含显式片段参数的作用域。
     *
     * @param parameters 已在调用方解析完成的有序参数
     * @return 不捕获调用方循环变量的片段作用域
     */
    BindingScope fragment(Map<String, JsonNode> parameters) {
        BindingScope scope = rootOnly();
        for (Map.Entry<String, JsonNode> entry : parameters.entrySet()) {
            scope = scope.child(entry.getKey(), entry.getValue());
        }
        return scope;
    }

    /**
     * 解析已经编译的数据路径。
     *
     * @param path 受限路径
     * @return 区分缺失和显式空值的解析结果
     */
    ResolvedValue resolve(CompiledDataPath path) {
        JsonNode current;
        if (path.variableName() == null) {
            current = root;
        } else {
            current = variables.get(path.variableName());
            if (current == null) {
                return ResolvedValue.missing();
            }
        }
        for (String segment : path.segments()) {
            if (current == null || !current.isObject()) {
                return ResolvedValue.invalid();
            }
            if (!current.has(segment)) {
                return ResolvedValue.missing();
            }
            current = current.get(segment);
        }
        return ResolvedValue.present(current.deepCopy());
    }

    /**
     * 为可信扩展创建与内部状态隔离的只读数据视图。
     *
     * @return 当前根数据和词法变量的防御性快照
     */
    PrintDataView dataView() {
        if (dataView == null) {
            dataView = parent == null
                    ? PrintDataView.of(root, Map.of())
                    : parent.dataView().withVariable(boundName, boundValue);
        }
        return dataView;
    }

    /**
     * 数据路径解析结果。
     *
     * @author leyland
     */
    static final class ResolvedValue {

        /** 路径解析状态。 */
        private final State state;

        /** 路径值；缺失时为 {@code null}。 */
        private final JsonNode value;

        /** 创建解析结果。 */
        private ResolvedValue(State state, JsonNode value) {
            this.state = state;
            this.value = value;
        }

        /** @return 缺失结果 */
        static ResolvedValue missing() {
            return new ResolvedValue(State.MISSING, null);
        }

        /** @return 无法继续遍历的结果 */
        static ResolvedValue invalid() {
            return new ResolvedValue(State.INVALID, null);
        }

        /** @return 存在结果 */
        static ResolvedValue present(JsonNode value) {
            return new ResolvedValue(State.PRESENT, value);
        }

        /** @return 路径是否存在 */
        boolean isPresent() {
            return state == State.PRESENT;
        }

        /** @return 路径是否因为节点类型不支持继续遍历而失败 */
        boolean isInvalid() {
            return state == State.INVALID;
        }

        /** @return 路径值 */
        JsonNode value() {
            return value;
        }

        /** 路径解析状态。 */
        private enum State {
            PRESENT, MISSING, INVALID
        }
    }
}
