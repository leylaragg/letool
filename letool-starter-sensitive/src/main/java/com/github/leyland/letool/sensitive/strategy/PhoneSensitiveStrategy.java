package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 手机号脱敏 —— 默认保留前 3 位 + 后 4 位，中间用 '*' 填充.
 *
 * <pre>
 *   "13812345678" → "138****5678"
 *   "1381234"      → "1381234"（长度不足 7 位不处理）
 * </pre>
 *
 * <p>可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class PhoneSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        // 长度不足 7 位的不处理（可能是固话或其他短号码）
        if (value == null || value.length() < 7) return value;

        // 从 context 获取保留长度，context 为 null 或值为 -1 时使用策略默认值
        int prefix = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : 3;
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
