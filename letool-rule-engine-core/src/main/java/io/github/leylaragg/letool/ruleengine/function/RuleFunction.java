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
     * 声明函数是否读取显式参数以外的事实。
     *
     * <p>既有函数默认按动态事实处理，避免框架在没有明确声明时错误地把静态依赖
     * 视为完整。确认只使用参数的纯函数应显式返回
     * {@link FunctionFactAccess#EXPLICIT_ARGUMENTS_ONLY}。</p>
     *
     * @return 保守的事实访问声明
     */
    default FunctionFactAccess factAccess() {
        return FunctionFactAccess.DYNAMIC_FACTS;
    }

    /**
     * 执行函数。
     *
     * @param arguments 不可变事实值参数
     * @param context 只读调用上下文
     * @return 符合声明返回类型的事实值
     */
    FactValue execute(FunctionArguments arguments, FunctionContext context);
}
