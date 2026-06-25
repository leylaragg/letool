package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 保留首尾长度脱敏 —— 保留前 N 位 + 后 M 位，中间用遮盖字符填充，适用于未分类的通用字段.
 *
 * <pre>
 *   // 默认保留首 1 位 + 尾 1 位
 *   "ABCDEF" → "A****F"
 *
 *   // 通过 MaskContext 自定义
 *   context.setKeepPrefix(2); context.setKeepSuffix(3);
 *   "ABCDEFGH" → "AB***FGH"
 * </pre>
 *
 * <p>这是一个通用策略，不预设特定业务语义.
 * 保留长度完全由 {@link MaskContext#getKeepPrefix()} / {@link MaskContext#getKeepSuffix()} 控制.</p>
 */
public class KeepLengthSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // 从 context 获取保留长度，context 为 null 或值为 -1 时使用默认值（首尾各 1 位）
        int prefix = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : 1;
        int suffix = context != null && context.getKeepSuffix() > 0 ? context.getKeepSuffix() : 1;
        char ch = context != null ? context.getMaskChar() : '*';

        // 原始长度不足以保留 → 原样返回
        if (value.length() <= prefix + suffix) return value;

        // 三部分拼接：前 N 位 + 遮盖区 + 后 M 位
        return value.substring(0, prefix)
                + String.valueOf(ch).repeat(value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }
}
