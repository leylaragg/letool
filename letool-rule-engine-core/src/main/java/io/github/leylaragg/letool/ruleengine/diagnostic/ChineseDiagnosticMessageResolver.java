package io.github.leylaragg.letool.ruleengine.diagnostic;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.util.Locale;

/**
 * 确定性的默认中文诊断消息渲染器。
 *
 * <p>当前实现对所有区域设置使用中文回退文本，避免未知区域导致消息缺失。</p>
 */
public final class ChineseDiagnosticMessageResolver implements DiagnosticMessageResolver {

    /**
     * 使用诊断码自带的中文兜底文案，并交给统一安全边界追加参数。
     *
     * @param diagnostic 结构化诊断
     * @param locale 展示区域设置；当前只用于校验调用契约
     * @return 确定性中文文本
     */
    @Override
    public String resolve(RuleDiagnostic diagnostic, Locale locale) {
        if (diagnostic == null || locale == null) {
            throw RuleEngineException.invalidArgument();
        }
        return DiagnosticMessageFormatter.format(
                diagnostic, diagnostic.code().getDefaultMessage());
    }
}
