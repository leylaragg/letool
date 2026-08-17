package io.github.leylaragg.letool.ruleengine.function;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 不可变函数调用签名。
 */
public final class FunctionSignature {

    /** 单次函数签名和调用允许的最大参数数量。 */
    public static final int MAX_ARGUMENT_COUNT = 256;

    /** 零参数签名共享实例。 */
    private static final FunctionSignature EMPTY = new FunctionSignature(List.of());

    /** 按声明顺序冻结的参数。 */
    private final List<FunctionParameter> parameters;

    /** 必填参数数量。 */
    private final int minimumArgumentCount;

    /** 普通签名为声明数，可变签名为全局参数上限。 */
    private final int maximumArgumentCount;

    /** 最后一个参数是否接收剩余实参。 */
    private final boolean varargs;

    /** 校验参数顺序规则并预计算实参数量边界。 */
    private FunctionSignature(List<FunctionParameter> parameters) {
        this.parameters = validateAndCopy(parameters);
        this.minimumArgumentCount = minimumCount(this.parameters);
        this.varargs = !this.parameters.isEmpty()
                && this.parameters.get(this.parameters.size() - 1).varargs();
        this.maximumArgumentCount = varargs ? MAX_ARGUMENT_COUNT : this.parameters.size();
    }

    /**
     * 共享的零参数签名。
     *
     * @return 零参数签名单例
     */
    public static FunctionSignature empty() {
        return EMPTY;
    }

    /**
     * 按参数顺序创建签名。
     *
     * <p>签名最多包含二百五十六个参数。</p>
     *
     * @param parameters 参数序列
     * @return 不可变签名
     */
    public static FunctionSignature of(FunctionParameter... parameters) {
        if (parameters == null) throw RuleEngineException.invalidArgument();
        if (parameters.length > MAX_ARGUMENT_COUNT) throw RuleEngineException.invalidArgument();
        return parameters.length == 0 ? EMPTY : new FunctionSignature(Arrays.asList(parameters));
    }

    /**
     * 按参数列表创建签名。
     *
     * <p>签名最多包含二百五十六个参数，并通过有界迭代复制外部列表。</p>
     *
     * @param parameters 参数列表
     * @return 不可变签名
     */
    public static FunctionSignature of(List<FunctionParameter> parameters) {
        if (parameters == null) throw RuleEngineException.invalidArgument();
        FunctionSignature signature = new FunctionSignature(parameters);
        return signature.parameters.isEmpty() ? EMPTY : signature;
    }

    /**
     * 声明顺序不变的只读参数列表。
     *
     * @return 参数列表
     */
    public List<FunctionParameter> parameters() {
        return parameters;
    }

    /**
     * 调用签名所需的最少实参数量。
     *
     * @return 最少实参数量
     */
    public int minimumArgumentCount() {
        return minimumArgumentCount;
    }

    /**
     * 调用签名允许的最多实参数量；可变签名同样受 {@link #MAX_ARGUMENT_COUNT} 约束。
     *
     * @return 最多实参数量
     */
    public int maximumArgumentCount() {
        return maximumArgumentCount;
    }

    /**
     * 判断签名是否包含尾部可变参数。
     *
     * @return 包含可变参数时返回 {@code true}
     */
    public boolean hasVarargs() {
        return varargs;
    }

    /**
     * 判断实参数量是否符合签名。
     *
     * @param argumentCount 实参数量
     * @return 数量合法时返回 {@code true}
     */
    public boolean acceptsArgumentCount(int argumentCount) {
        if (argumentCount < 0) throw RuleEngineException.invalidArgument();
        return argumentCount >= minimumArgumentCount && argumentCount <= maximumArgumentCount;
    }

    /** 有界复制参数，并校验名称唯一、可选参数和可变参数顺序。 */
    private static List<FunctionParameter> validateAndCopy(List<FunctionParameter> source) {
        List<FunctionParameter> copy = new ArrayList<>();
        try {
            for (FunctionParameter parameter : source) {
                if (copy.size() == MAX_ARGUMENT_COUNT || parameter == null) {
                    throw RuleEngineException.invalidArgument();
                }
                copy.add(parameter);
            }
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
        copy = List.copyOf(copy);
        Set<String> names = new HashSet<>();
        boolean optionalSeen = false;
        boolean varargsSeen = false;
        for (int index = 0; index < copy.size(); index++) {
            FunctionParameter parameter = copy.get(index);
            if (!names.add(parameter.name())) throw RuleEngineException.invalidArgument();
            if (varargsSeen) throw RuleEngineException.invalidArgument();
            if (parameter.varargs()) {
                if (index != copy.size() - 1) throw RuleEngineException.invalidArgument();
                varargsSeen = true;
            } else if (parameter.optional()) {
                optionalSeen = true;
            } else if (optionalSeen) {
                throw RuleEngineException.invalidArgument();
            }
        }
        return copy;
    }

    /** 统计签名中的必填参数数量。 */
    private static int minimumCount(List<FunctionParameter> parameters) {
        int count = 0;
        for (FunctionParameter parameter : parameters) {
            if (!parameter.optional()) count++;
        }
        return count;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof FunctionSignature that && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters);
    }

    @Override
    public String toString() {
        return parameters.toString();
    }
}
