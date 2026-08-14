package com.github.leyland.letool.ruleengine.diagnostic;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 为诊断基础文案追加稳定编码和安全参数。
 *
 * <p>只渲染 {@link RuleDiagnostic} 的安全快照，不使用通用 {@code toString()} 兜底。</p>
 */
public final class DiagnosticMessageFormatter {

    /** 基础文案最多允许二千零四十八个 UTF-16 代码单元。 */
    private static final int MAX_BASE_MESSAGE_LENGTH = 2048;

    /** 禁止实例化无状态工具类。 */
    private DiagnosticMessageFormatter() {
    }

    /**
     * 按固定协议拼接诊断编码、基础文案和有序参数，并清理不安全 Unicode 字符。
     *
     * @param diagnostic 已完成参数快照和类型边界校验的结构化诊断
     * @param baseMessage 消息源解析出的非空白基础文案
     * @return 带稳定编码前缀并按原顺序追加安全参数的展示文本
     * @throws RuleEngineException 诊断为空、基础文案无效或参数状态不满足安全契约时抛出
     */
    public static String format(RuleDiagnostic diagnostic, String baseMessage) {
        if (diagnostic == null || baseMessage == null
                || baseMessage.length() > MAX_BASE_MESSAGE_LENGTH) {
            throw RuleEngineException.invalidArgument();
        }
        String safeBaseMessage = sanitizeText(baseMessage);
        if (safeBaseMessage.isBlank()) {
            throw RuleEngineException.invalidArgument();
        }

        StringBuilder message = new StringBuilder()
                .append('[').append(diagnostic.code().getCode()).append("] ")
                .append(safeBaseMessage);
        if (!diagnostic.arguments().isEmpty()) {
            message.append('：');
            for (int index = 0; index < diagnostic.arguments().size(); index++) {
                if (index > 0) {
                    message.append('，');
                }
                message.append(renderArgument(diagnostic.arguments().get(index)));
            }
        }
        return message.toString();
    }

    /**
     * 按精确 JDK 类型渲染参数，白名单变化时必须同步审查这里。
     *
     * @param argument 已由诊断对象保存的单个安全参数
     * @return 与区域设置无关且长度受诊断参数契约约束的文本
     * @throws RuleEngineException 参数状态与诊断快照契约不一致时抛出
     */
    private static String renderArgument(Object argument) {
        if (argument instanceof String text) {
            return sanitizeText(text);
        }
        if (argument == null) {
            throw RuleEngineException.invalidArgument();
        }
        Class<?> type = argument.getClass();
        if (type == Boolean.class) {
            return ((Boolean) argument).toString();
        }
        if (type == BigInteger.class) {
            return ((BigInteger) argument).toString();
        }
        if (type == BigDecimal.class) {
            return ((BigDecimal) argument).toPlainString();
        }
        if (type == LocalDate.class) {
            return ((LocalDate) argument).toString();
        }
        if (type == LocalDateTime.class) {
            return ((LocalDateTime) argument).toString();
        }
        if (type == Instant.class) {
            return ((Instant) argument).toString();
        }
        throw RuleEngineException.invalidArgument();
    }

    /**
     * 等长替换控制字符、Unicode 行段分隔符和孤立代理项。
     *
     * @param text 需要进入最终诊断消息的可信类型字符串
     * @return 与输入等长且不含 ISO 控制字符的文本
     */
    private static String sanitizeText(String text) {
        StringBuilder sanitized = null;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isHighSurrogate(character)
                    && index + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(index + 1))) {
                index++;
                continue;
            }
            if (Character.isISOControl(character)
                    || character == '\u2028'
                    || character == '\u2029'
                    || Character.isSurrogate(character)) {
                if (sanitized == null) {
                    sanitized = new StringBuilder(text);
                }
                sanitized.setCharAt(index, ' ');
            }
        }
        return sanitized == null ? text : sanitized.toString();
    }
}
