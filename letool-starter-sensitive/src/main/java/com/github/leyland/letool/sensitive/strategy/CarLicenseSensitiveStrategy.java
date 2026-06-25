package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 车牌号脱敏 —— 保留前 2 位（省份简称 + 城市代码）+ 最后 1 位，中间用 {@code *} 填充.
 *
 * <pre>
 *   "京A12345"  → "京A****5"
 *   "沪C88888"  → "沪C****8"
 *   "粤B1234"   → "粤B1234"（长度不足 3 位不处理）
 * </pre>
 *
 * <p>可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class CarLicenseSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        // 长度不足 3 位的不处理（非标准车牌号）
        if (value == null || value.length() < 3) return value;

        // 从 context 获取保留长度，context 为 null 或值为 -1 时使用策略默认值
        int prefix = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : 2;
        int suffix = context != null && context.getKeepSuffix() > 0 ? context.getKeepSuffix() : 1;
        char ch = context != null ? context.getMaskChar() : '*';

        // 原始长度不足以保留 → 原样返回
        if (value.length() <= prefix + suffix) return value;

        // 三部分拼接：前缀（省份+城市）+ 遮盖区 + 后缀（末位）
        return value.substring(0, prefix)
                + String.valueOf(ch).repeat(value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }
}
