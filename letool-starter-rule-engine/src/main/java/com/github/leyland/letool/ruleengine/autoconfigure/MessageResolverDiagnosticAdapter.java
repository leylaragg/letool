package com.github.leyland.letool.ruleengine.autoconfigure;

import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticMessageFormatter;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticMessageResolver;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.Locale;
import java.util.Objects;

/** 把通用国际化文案接入规则诊断的安全格式化边界。 */
final class MessageResolverDiagnosticAdapter implements DiagnosticMessageResolver {

    /** 由异常模块提供的通用国际化解析器。 */
    private final MessageResolver messageResolver;

    /**
     * 创建无状态适配器。
     *
     * @param messageResolver 通用消息解析器
     */
    MessageResolverDiagnosticAdapter(MessageResolver messageResolver) {
        this.messageResolver = Objects.requireNonNull(messageResolver, "messageResolver");
    }

    /**
     * 先解析无动态参数的基础文案，再由规则诊断边界安全追加参数。
     *
     * @param diagnostic 结构化规则诊断
     * @param locale 展示语言环境；为空时交由通用解析器回退
     * @return 带稳定诊断码前缀的安全文案
     * @throws RuleEngineException 诊断或不可信解析结果无效时抛出固定安全异常
     */
    @Override
    public String resolve(RuleDiagnostic diagnostic, Locale locale) {
        try {
            if (diagnostic == null) {
                throw RuleEngineException.invalidArgument();
            }
            String baseMessage = messageResolver.resolve(diagnostic.code(), locale);
            return DiagnosticMessageFormatter.format(diagnostic, baseMessage);
        } catch (RuntimeException exception) {
            // 不保留不可信解析器的异常文本和原因链，避免跨边界泄漏资源细节。
            throw RuleEngineException.invalidArgument();
        }
    }
}
