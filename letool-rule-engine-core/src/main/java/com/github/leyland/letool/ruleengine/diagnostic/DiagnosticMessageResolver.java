package com.github.leyland.letool.ruleengine.diagnostic;

import java.util.Locale;

/**
 * 在展示阶段将结构化诊断渲染为区域化文本。
 */
@FunctionalInterface
public interface DiagnosticMessageResolver {

    /**
     * 渲染诊断消息。
     *
     * @param diagnostic 结构化诊断
     * @param locale 展示区域设置
     * @return 安全的展示文本
     */
    String resolve(RuleDiagnostic diagnostic, Locale locale);
}
