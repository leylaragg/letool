package com.github.leyland.letool.ruleengine.type;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

/**
 * 可空性明确的不可变类型描述。
 */
public final class TypeDescriptor {

    /** 防止规范文本、相等和哈希计算遭遇无界嵌套。 */
    private static final int MAX_ARRAY_DEPTH = 64;

    /** 类型目录中的基本分类。 */
    private final TypeKind kind;

    /** 当前值本身是否允许为空。 */
    private final boolean nullable;

    /** 数组元素类型；非数组类型为 {@code null}。 */
    private final TypeDescriptor elementType;

    /** 预计算数组嵌套层数。 */
    private final int arrayDepth;

    /** 接收已经校验的类型结构。 */
    private TypeDescriptor(
            TypeKind kind,
            boolean nullable,
            TypeDescriptor elementType,
            int arrayDepth) {
        this.kind = kind;
        this.nullable = nullable;
        this.elementType = elementType;
        this.arrayDepth = arrayDepth;
    }

    /**
     * 创建标量或未知占位类型。
     *
     * @param kind 非数组、非对象类型
     * @param nullable 是否允许空值
     * @return 类型描述
     */
    public static TypeDescriptor scalar(TypeKind kind, boolean nullable) {
        if (kind == null || kind == TypeKind.ARRAY || kind == TypeKind.OBJECT) {
            throw RuleEngineException.invalidArgument();
        }
        return new TypeDescriptor(kind, nullable, null, 0);
    }

    /**
     * 创建数组类型。
     *
     * <p>为避免规范字符串、相等判断和哈希计算遭受非受控递归，数组最多嵌套六十四层。</p>
     *
     * @param elementType 非空且数组深度小于六十四层的元素类型
     * @param nullable 数组自身是否允许空值
     * @return 数组类型描述
     */
    public static TypeDescriptor array(TypeDescriptor elementType, boolean nullable) {
        if (elementType == null || elementType.arrayDepth >= MAX_ARRAY_DEPTH) {
            throw RuleEngineException.invalidArgument();
        }
        return new TypeDescriptor(TypeKind.ARRAY, nullable, elementType,
                elementType.arrayDepth + 1);
    }

    /**
     * 创建对象类型。
     *
     * @param nullable 是否允许空值
     * @return 对象类型描述
     */
    public static TypeDescriptor object(boolean nullable) {
        return new TypeDescriptor(TypeKind.OBJECT, nullable, null, 0);
    }

    /**
     * 类型目录中的基本分类。
     *
     * @return 类型分类
     */
    public TypeKind kind() {
        return kind;
    }

    /**
     * 判断当前值是否允许为空。
     *
     * @return 允许为空时返回 {@code true}
     */
    public boolean nullable() {
        return nullable;
    }

    /**
     * 数组的直接元素类型。
     *
     * @return 数组元素类型
     * @throws RuleEngineException 当前类型不是数组时抛出
     */
    public TypeDescriptor elementType() {
        if (kind != TypeKind.ARRAY) {
            throw RuleEngineException.invalidArgument();
        }
        return elementType;
    }

    /**
     * 参与契约指纹的稳定规范字符串。
     *
     * @return 规范类型字符串
     */
    public String toCanonicalString() {
        StringBuilder canonical = new StringBuilder();
        TypeDescriptor current = this;
        boolean[] nullability = new boolean[arrayDepth];
        int index = 0;
        while (current.kind == TypeKind.ARRAY) {
            canonical.append("ARRAY<");
            nullability[index++] = current.nullable;
            current = current.elementType;
        }
        canonical.append(current.kind.name())
                .append(current.nullable ? '?' : '!');
        for (int depth = nullability.length - 1; depth >= 0; depth--) {
            canonical.append('>')
                    .append(nullability[depth] ? '?' : '!');
        }
        return canonical.toString();
    }

    /** 迭代比较数组链，避免深层类型触发递归。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TypeDescriptor that)) {
            return false;
        }
        TypeDescriptor left = this;
        TypeDescriptor right = that;
        while (true) {
            if (left.nullable != right.nullable || left.kind != right.kind) return false;
            if (left.kind != TypeKind.ARRAY) return true;
            left = left.elementType;
            right = right.elementType;
        }
    }

    /** 迭代计算数组链哈希，避免深层类型触发递归。 */
    @Override
    public int hashCode() {
        int result = 1;
        TypeDescriptor current = this;
        while (true) {
            result = 31 * result + current.kind.hashCode();
            result = 31 * result + Boolean.hashCode(current.nullable);
            if (current.kind != TypeKind.ARRAY) return result;
            current = current.elementType;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return toCanonicalString();
    }
}
