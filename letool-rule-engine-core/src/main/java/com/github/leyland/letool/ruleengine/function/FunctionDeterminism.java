package com.github.leyland.letool.ruleengine.function;

/**
 * 函数结果确定性。
 */
public enum FunctionDeterminism {
    /** 相同输入与上下文产生相同结果。 */
    DETERMINISTIC,
    /** 结果可能随时间或外部会话状态变化。 */
    NON_DETERMINISTIC
}
