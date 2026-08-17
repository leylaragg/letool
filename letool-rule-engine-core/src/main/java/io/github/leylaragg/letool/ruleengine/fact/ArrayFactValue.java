package io.github.leylaragg.letool.ruleengine.fact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 不可变有序数组事实值。
 */
public final class ArrayFactValue implements FactValue {

    /** 按输入顺序冻结的已规范化元素。 */
    private final List<FactValue> elements;

    /**
     * 创建数组事实值并复制元素列表。
     *
     * @param elements 已规范化元素
     */
    ArrayFactValue(List<FactValue> elements) {
        if (elements == null || elements.stream().anyMatch(Objects::isNull)) {
            throw io.github.leylaragg.letool.ruleengine.exception.RuleEngineException.invalidArgument();
        }
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    @Override
    public FactKind kind() {
        return FactKind.ARRAY;
    }

    /**
     * 包内路径解析使用的容错下标查询。
     *
     * @param index 非负下标
     * @return 元素值，下标越界时为 {@code null}
     */
    FactValue element(int index) {
        return index < elements.size() ? elements.get(index) : null;
    }

    /**
     * 不展开任何子元素的数组容量。
     *
     * @return 元素数量
     */
    public int size() {
        return elements.size();
    }

    /**
     * 供类型校验和安全求值使用的不可变元素视图。
     *
     * @return 不可变元素列表
     */
    public List<FactValue> values() {
        return elements;
    }

    @Override
    public Object toSafeJavaValue() {
        List<Object> result = new ArrayList<>(elements.size());
        for (FactValue element : elements) {
            result.add(element.toSafeJavaValue());
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ArrayFactValue that && elements.equals(that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements);
    }

    @Override
    public String toString() {
        return elements.toString();
    }
}
