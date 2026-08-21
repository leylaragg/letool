package io.github.leylaragg.letool.print.template.inspection;

import io.github.leylaragg.letool.print.document.node.DocumentNode;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 统一收口检查模型的安全值和有序快照规则。
 *
 * @author leyland
 */
final class InspectionValues {

    /** 模板代码沿用模板集合使用的稳定标识形式。 */
    private static final Pattern TEMPLATE_CODE =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    /** 输出格式、扩展名和参数名使用不含执行语义的受限标识。 */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    /** 词法变量和片段参数允许保留 Java 风格大小写。 */
    private static final Pattern SCOPE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    /** 工具类不允许实例化。 */
    private InspectionValues() {
    }

    /** 校验模板代码并返回原值。 */
    static String templateCode(String value, String name) {
        if (value == null || !TEMPLATE_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 不合法");
        }
        return value;
    }

    /** 校验公共扩展标识并返回原值。 */
    static String identifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 不合法");
        }
        return value;
    }

    /** 校验词法作用域名称并返回原值。 */
    static String scopeName(String value, String name) {
        if (value == null || !SCOPE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 不合法");
        }
        return value;
    }

    /** 接受已经过模板前端校验的路径，但拒绝空白和失控长度。 */
    static String dataPath(String value) {
        if (value == null || value.isBlank() || value.length() > 512
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("dataPath 不合法");
        }
        return value;
    }

    /** 标签路径只用于安全定位，不能带控制字符或无限增长。 */
    static String tagPath(String value) {
        if (value == null || value.isBlank() || value.length() > 2_048
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("tagPath 不合法");
        }
        return value;
    }

    /** 复制有序字符串集合，并逐项执行指定的名称校验。 */
    static Set<String> orderedNames(Set<String> source, boolean scopeNames, String name) {
        Objects.requireNonNull(source, name + " 不能为空");
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String value : source) {
            copy.add(scopeNames ? scopeName(value, name) : identifier(value, name));
        }
        return Collections.unmodifiableSet(copy);
    }

    /** 复制有序节点类型集合，并确保输出端拿到可精确判断的具体类型。 */
    static Set<Class<? extends DocumentNode>> nodeTypes(
            Set<Class<? extends DocumentNode>> source) {
        Objects.requireNonNull(source, "nodeTypes 不能为空");
        LinkedHashSet<Class<? extends DocumentNode>> copy = new LinkedHashSet<>();
        for (Class<? extends DocumentNode> nodeType : source) {
            Class<? extends DocumentNode> checkedType =
                    Objects.requireNonNull(nodeType, "nodeType 不能为空");
            if (checkedType.isInterface() || Modifier.isAbstract(checkedType.getModifiers())) {
                throw new IllegalArgumentException("nodeType 必须是具体类型");
            }
            copy.add(checkedType);
        }
        return Collections.unmodifiableSet(copy);
    }

    /** 深复制片段参数表，保留模板和参数的声明顺序。 */
    static Map<String, List<String>> fragmentParameters(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "fragmentParameters 不能为空");
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((templateCode, parameters) -> {
            templateCode(templateCode, "fragmentTemplateCode");
            Objects.requireNonNull(parameters, "fragmentParameters value 不能为空");
            List<String> parameterCopy = parameters.stream()
                    .map(value -> scopeName(value, "fragmentParameter"))
                    .toList();
            if (new LinkedHashSet<>(parameterCopy).size() != parameterCopy.size()) {
                throw new IllegalArgumentException("fragmentParameter 不能重复");
            }
            copy.put(templateCode, parameterCopy);
        });
        return Collections.unmodifiableMap(copy);
    }

    /** 深复制 include 参数映射，保留调用处声明顺序。 */
    static Map<String, String> includeArguments(Map<String, String> source) {
        Objects.requireNonNull(source, "arguments 不能为空");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        source.forEach((name, path) -> copy.put(
                scopeName(name, "argumentName"), dataPath(path)));
        return Collections.unmodifiableMap(copy);
    }
}
