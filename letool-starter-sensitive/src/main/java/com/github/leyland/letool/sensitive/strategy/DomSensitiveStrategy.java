package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 军官证 / 港澳通行证脱敏 —— 保留首字（证件类型）+ 后 4 位，中间用 {@code *} 填充.
 *
 * <pre>
 *   "军1234567"  → "军****567"
 *   "H12345678"  → "H****5678"（港澳通行证）
 *   "军12"        → "军12"（长度不足 3 位不处理）
 * </pre>
 *
 * <p>适用于军官证、士兵证、港澳通行证、台湾通行证等非标准证件号码.
 * 可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class DomSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        // 长度不足 3 位的不处理
        if (value == null || value.length() < 3) return value;

        // 从 context 获取保留长度，context 为 null 或值为 -1 时使用策略默认值
        int prefix = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : 1;
        int suffix = context != null && context.getKeepSuffix() > 0 ? context.getKeepSuffix() : 4;
        char ch = context != null ? context.getMaskChar() : '*';

        // 原始长度不足以保留 → 原样返回
        if (value.length() <= prefix + suffix) return value;

        // 三部分拼接：证件类型首字 + 遮盖区 + 后 4 位
        return value.substring(0, prefix)
                + String.valueOf(ch).repeat(value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }
}
