package com.github.leyland.letool.ruleengine.function;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 不可变函数参数元数据。
 */
public final class FunctionParameter {

    /** 参数名允许的 ASCII 标识符格式。 */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");

    /** 函数签名内唯一的参数名。 */
    private final String name;

    /** 每个对应实参的预期类型。 */
    private final TypeDescriptor type;

    /** 调用时是否可以省略。 */
    private final boolean optional;

    /** 是否接收尾部剩余实参。 */
    private final boolean varargs;

    /** 接收单个参数的完整签名元数据。 */
    private FunctionParameter(
            String name,
            TypeDescriptor type,
            boolean optional,
            boolean varargs) {
        if (name == null || !NAME_PATTERN.matcher(name).matches() || type == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.name = name;
        this.type = type;
        this.optional = optional;
        this.varargs = varargs;
    }

    /**
     * 创建必填参数。
     *
     * @param name 参数名
     * @param type 参数类型
     * @return 必填参数
     */
    public static FunctionParameter required(String name, TypeDescriptor type) {
        return new FunctionParameter(name, type, false, false);
    }

    /**
     * 创建尾部可选参数。
     *
     * @param name 参数名
     * @param type 参数类型
     * @return 可选参数
     */
    public static FunctionParameter optional(String name, TypeDescriptor type) {
        return new FunctionParameter(name, type, true, false);
    }

    /**
     * 创建尾部可变参数。
     *
     * @param name 参数名
     * @param type 每个附加实参的类型
     * @return 可变参数
     */
    public static FunctionParameter varargs(String name, TypeDescriptor type) {
        return new FunctionParameter(name, type, true, true);
    }

    /**
     * 在函数签名内唯一的参数名。
     *
     * @return 参数名
     */
    public String name() {
        return name;
    }

    /**
     * 编译期实参检查使用的预期类型。
     *
     * @return 参数类型
     */
    public TypeDescriptor type() {
        return type;
    }

    /**
     * 判断参数是否可省略。
     *
     * @return 可省略时返回 {@code true}
     */
    public boolean optional() {
        return optional;
    }

    /**
     * 判断参数是否接收尾部可变实参。
     *
     * @return 可变参数时返回 {@code true}
     */
    public boolean varargs() {
        return varargs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FunctionParameter that)) return false;
        return optional == that.optional && varargs == that.varargs
                && name.equals(that.name) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, optional, varargs);
    }

    @Override
    public String toString() {
        return name + ":" + type.toCanonicalString()
                + (varargs ? "..." : optional ? "?" : "!");
    }
}
