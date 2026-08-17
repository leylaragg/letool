package io.github.leylaragg.letool.ruleengine.diagnostic;

/**
 * 规则诊断产生阶段。
 */
public enum DiagnosticPhase {

    /** 词法扫描阶段。 */
    LEXICAL,

    /** 语法解析阶段。 */
    SYNTAX,

    /** 语义分析阶段。 */
    SEMANTIC,

    /** 表达式求值阶段。 */
    RUNTIME,

    /** 动作规划阶段。 */
    PLANNING
}
