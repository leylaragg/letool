package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;
import com.github.leyland.letool.sensitive.exception.SensitiveException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则表达式脱敏 —— 用正则匹配敏感内容并替换，适用于 {@link com.github.leyland.letool.sensitive.core.SensitiveType#CUSTOM} 类型.
 *
 * <pre>
 *   // 遮盖工号中的数字部分
 *   context = context.withPattern("(?<=工号)\\d{4}").withReplacement("****");
 *   mask("工号123456", context) → "工号****56"
 *
 *   // 遮盖邮箱域名
 *   context = context.withPattern("(?<=@)\\w+").withReplacement("***");
 *   mask("user@company.com", context) → "user@***.com"
 * </pre>
 *
 * <p>正则和替换字符均从 {@link MaskContext} 获取：
 * <ul>
 *   <li>{@link MaskContext#getPattern()} —— 必填正则表达式，缺失时抛出配置异常</li>
 *   <li>{@link MaskContext#getReplacement()} —— 替换字符串，默认 "*"</li>
 * </ul>
 *
 * <p>此策略是唯一强制依赖 context 不为 null 的策略 —— 因为正则表达式必须由注解或调用方提供.</p>
 */
public class RegexSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    /**
     * 按当前策略执行单值脱敏。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param context 脱敏上下文，可为 {@code null} 以使用策略默认值
     * @return 脱敏结果；空值保持不变
     */
    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (context == null || context.getPattern() == null || context.getPattern().isEmpty()) {
            throw SensitiveException.configurationInvalid("CUSTOM 类型必须配置 pattern");
        }

        Matcher matcher = Pattern.compile(context.getPattern()).matcher(value);
        if (!matcher.find()) {
            return MaskingSupport.maskAll(value, MaskingSupport.maskChar(context));
        }
        String masked = matcher.replaceAll(MaskingSupport.replacement(context, "*"));
        return masked.equals(value)
                ? MaskingSupport.maskAll(value, MaskingSupport.maskChar(context))
                : masked;
    }
}
