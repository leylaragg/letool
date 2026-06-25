package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 身份证脱敏 —— 保留前 4 位（地区码）+ 后 4 位（校验码），中间用 {@code *} 填充.
 *
 * <pre>
 *   "320123199001011234" → "3201**********1234"
 *   "320123"              → "320123"（长度不足 8 位不处理）
 * </pre>
 *
 * <p>可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class IdCardSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        // 长度不足 8 位的不处理（非标准身份证号码）
        if (value == null || value.length() < 8) return value;

        // 从 context 获取保留长度，context 为 null 或值为 -1 时使用策略默认值
        int prefix = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : 4;
        int suffix = context != null && context.getKeepSuffix() > 0 ? context.getKeepSuffix() : 4;
        char ch = context != null ? context.getMaskChar() : '*';

        // 原始长度不足以保留 → 原样返回
        if (value.length() <= prefix + suffix) return value;

        // 三部分拼接：前缀 + 遮盖区 + 后缀
        return value.substring(0, prefix)
                + String.valueOf(ch).repeat(value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }
}
