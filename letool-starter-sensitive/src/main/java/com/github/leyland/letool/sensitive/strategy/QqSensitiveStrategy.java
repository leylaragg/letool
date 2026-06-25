package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * QQ 号脱敏 —— 保留前 2 位 + 后 2 位，中间用 {@code *} 填充.
 *
 * <pre>
 *   "1234567890" → "12******90"
 *   "12345"       → "12345"（长度不足 5 位不处理）
 * </pre>
 *
 * <p>可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class QqSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        // 长度不足 5 位的不处理（QQ 号最短 5 位）
        if (value == null || value.length() < 5) return value;

        // 从 context 获取保留长度，context 为 null 或值为 -1 时使用策略默认值
        int prefix = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : 2;
        int suffix = context != null && context.getKeepSuffix() > 0 ? context.getKeepSuffix() : 2;
        char ch = context != null ? context.getMaskChar() : '*';

        // 原始长度不足以保留 → 原样返回
        if (value.length() <= prefix + suffix) return value;

        // 三部分拼接：前 2 位 + 遮盖区 + 后 2 位
        return value.substring(0, prefix)
                + String.valueOf(ch).repeat(value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }
}
