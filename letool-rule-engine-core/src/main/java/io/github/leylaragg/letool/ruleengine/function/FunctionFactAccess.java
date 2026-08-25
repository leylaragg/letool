package io.github.leylaragg.letool.ruleengine.function;

/**
 * 声明函数在求值时如何读取事实。
 *
 * <p>这个声明只描述依赖可分析性，不授予新的宿主能力。函数仍只能通过现有只读
 * FunctionContext 获取事实。</p>
 */
public enum FunctionFactAccess {

    /** 函数只使用表达式显式传入的参数。 */
    EXPLICIT_ARGUMENTS_ONLY,

    /** 函数可能读取显式参数之外的事实，静态依赖集合因此不完整。 */
    DYNAMIC_FACTS
}
