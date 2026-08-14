package com.github.leyland.letool.ruleengine.function;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.fact.FactValue;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 单次函数调用的不可变事实值参数列表。
 */
public final class FunctionArguments {

    /** 零参数调用共享实例。 */
    private static final FunctionArguments EMPTY = new FunctionArguments(List.of());

    /** 按调用顺序冻结的事实值参数。 */
    private final List<FactValue> values;

    /** 接收已经完成容量和空值校验的参数列表。 */
    private FunctionArguments(List<FactValue> values) {
        this.values = values;
    }

    /**
     * 共享的零参数调用列表。
     *
     * @return 空参数列表
     */
    public static FunctionArguments empty() {
        return EMPTY;
    }

    /**
     * 从事实值列表创建参数快照。
     *
     * <p>一次调用最多包含二百五十六个参数；外部列表通过有界迭代复制。</p>
     *
     * @param values 事实值列表
     * @return 不可变参数列表
     */
    public static FunctionArguments of(List<? extends FactValue> values) {
        if (values == null) throw RuleEngineException.invalidArgument();
        try {
            List<FactValue> copy = new ArrayList<>();
            for (FactValue value : values) {
                if (copy.size() == FunctionSignature.MAX_ARGUMENT_COUNT || value == null) {
                    throw RuleEngineException.invalidArgument();
                }
                copy.add(value);
            }
            return copy.isEmpty() ? EMPTY : new FunctionArguments(List.copyOf(copy));
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /**
     * 从事实值序列创建参数快照。
     *
     * @param values 事实值序列
     * @return 不可变参数列表
     */
    public static FunctionArguments of(FactValue... values) {
        if (values == null) throw RuleEngineException.invalidArgument();
        if (values.length > FunctionSignature.MAX_ARGUMENT_COUNT) {
            throw RuleEngineException.invalidArgument();
        }
        return of(Arrays.asList(values));
    }

    /**
     * 本次调用的实参数量。
     *
     * @return 参数数量
     */
    public int size() {
        return values.size();
    }

    /**
     * 按零基索引读取参数。
     *
     * @param index 参数索引
     * @return 事实值参数
     */
    public FactValue get(int index) {
        if (index < 0 || index >= values.size()) throw RuleEngineException.invalidArgument();
        return values.get(index);
    }

    /**
     * 与调用方输入隔离的只读参数列表。
     *
     * @return 参数列表
     */
    public List<FactValue> values() {
        return values;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof FunctionArguments that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

}
