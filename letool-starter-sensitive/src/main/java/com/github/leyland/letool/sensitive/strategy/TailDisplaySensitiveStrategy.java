package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 仅展示尾部脱敏 —— 前缀全部遮盖，只保留末尾若干位（如支付尾号）.
 *
 * <pre>
 *   "6222021234567890" → "************7890"（默认保留后 4 位）
 *   "ORDER001"         → "***R001"           （通过 context 自定义 suffix=4）
 * </pre>
 *
 * <p>保留位数由 {@link MaskContext#getKeepSuffix()} 控制（默认 4 位）.
 * 可通过 {@link MaskContext#getMaskChar()} 覆盖默认遮盖字符.</p>
 */
public class TailDisplaySensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // 保留后 suffix 位（默认 4 位）
        int suffix = context != null && context.getKeepSuffix() > 0 ? context.getKeepSuffix() : 4;
        char ch = context != null ? context.getMaskChar() : '*';

        // 长度不足 → 原样返回
        if (value.length() <= suffix) return value;

        // 前缀全部遮盖 + 后 suffix 位明文
        return String.valueOf(ch).repeat(value.length() - suffix) + value.substring(value.length() - suffix);
    }
}
