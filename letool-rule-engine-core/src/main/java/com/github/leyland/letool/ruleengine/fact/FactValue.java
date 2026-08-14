package com.github.leyland.letool.ruleengine.fact;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.math.BigInteger;

/**
 * 不可变事实值的统一契约。
 */
public sealed interface FactValue permits ScalarFactValue, ObjectFactValue,
        ArrayFactValue, NullFactValue {

    /**
     * 值在规则类型系统中的分类。
     *
     * @return 稳定事实类型
     */
    FactKind kind();

    /**
     * 转换为不会泄漏内部可变状态的 Java 值。
     *
     * @return 安全 Java 值
     */
    Object toSafeJavaValue();

    /**
     * 以整数读取当前值。
     *
     * @return 大整数
     * @throws RuleEngineException 当前值不是整数时抛出
     */
    default BigInteger asBigInteger() {
        throw RuleEngineException.invalidArgument();
    }
}
