package io.github.leylaragg.letool.ruleengine.diagnostic;

/**
 * 规则诊断严重级别。
 */
public enum DiagnosticSeverity {

    /** 不阻止编译或求值的提示。 */
    INFO,

    /** 值得关注但不一定阻止处理的警告。 */
    WARNING,

    /** 阻止当前处理继续的错误。 */
    ERROR
}
