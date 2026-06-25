package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

import java.util.regex.Pattern;

/**
 * 正则表达式脱敏 —— 用正则匹配敏感内容并替换，适用于 {@link com.github.leyland.letool.sensitive.core.SensitiveType#CUSTOM} 类型.
 *
 * <pre>
 *   // 遮盖工号中的数字部分
 *   context.setPattern("(?<=工号)\\d{4}");
 *   context.setReplacement("****");
 *   mask("工号123456", context) → "工号****56"
 *
 *   // 遮盖邮箱域名
 *   context.setPattern("(?<=@)\\w+");
 *   context.setReplacement("***");
 *   mask("user@company.com", context) → "user@***.com"
 * </pre>
 *
 * <p>正则和替换字符均从 {@link MaskContext} 获取：
 * <ul>
 *   <li>{@link MaskContext#getPattern()} —— 正则表达式，为 null 或空字符串时不处理</li>
 *   <li>{@link MaskContext#getReplacement()} —— 替换字符串，默认 "*"</li>
 * </ul>
 *
 * <p>此策略是唯一强制依赖 context 不为 null 的策略 —— 因为正则表达式必须由注解或调用方提供.</p>
 */
public class RegexSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // context 为 null 或未提供正则 → 不处理（正则脱敏必须有明确的匹配规则）
        if (context == null || context.getPattern() == null || context.getPattern().isEmpty()) {
            return value;
        }

        // 编译正则并全局替换匹配到的内容
        return Pattern.compile(context.getPattern())
                .matcher(value)
                .replaceAll(context.getReplacement());
    }
}
