package com.github.leyland.letool.ruleengine.function;

/**
 * 函数允许读取的状态范围。
 */
public enum FunctionEffect {
    /** 只依赖显式参数。 */
    PURE,
    /** 可以读取只读函数上下文。 */
    CONTEXTUAL
}
