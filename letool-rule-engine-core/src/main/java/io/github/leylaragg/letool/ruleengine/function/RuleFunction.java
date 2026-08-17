package io.github.leylaragg.letool.ruleengine.function;

import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;

/**
 * 宿主按受限契约提供的规则函数。
 */
public interface RuleFunction {

    /** @return 函数编码 */
    String code();

    /** @return 函数语义版本 */
    String semanticVersion();

    /** @return 参数签名 */
    FunctionSignature signature();

    /** @return 返回类型 */
    TypeDescriptor returnType();

    /** @return 函数特征 */
    FunctionCharacteristics characteristics();

    /**
     * 执行函数。
     *
     * @param arguments 不可变事实值参数
     * @param context 只读调用上下文
     * @return 符合声明返回类型的事实值
     */
    FactValue execute(FunctionArguments arguments, FunctionContext context);
}
