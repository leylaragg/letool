package io.github.leylaragg.letool.ruleengine.fact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 以字符串键组织的不可变对象事实值。
 */
public final class ObjectFactValue implements FactValue {

    /** 保留输入顺序的不可变属性映射。 */
    private final Map<String, FactValue> properties;

    /**
     * 创建对象事实值并复制属性映射。
     *
     * @param properties 已规范化属性
     */
    ObjectFactValue(Map<String, FactValue> properties) {
        if (properties == null || properties.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)) {
            throw io.github.leylaragg.letool.ruleengine.exception.RuleEngineException.invalidArgument();
        }
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    @Override
    public FactKind kind() {
        return FactKind.OBJECT;
    }

    /**
     * 查询指定属性。
     *
     * @param name 属性名
     * @return 属性值，不存在时为 {@code null}
     */
    FactValue property(String name) {
        return properties.get(name);
    }

    /**
     * 不展开任何属性值的对象容量。
     *
     * @return 属性数量
     */
    public int size() {
        return properties.size();
    }

    @Override
    public Object toSafeJavaValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        properties.forEach((key, value) -> result.put(key, value.toSafeJavaValue()));
        return Collections.unmodifiableMap(result);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ObjectFactValue that && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    @Override
    public String toString() {
        return properties.toString();
    }
}
