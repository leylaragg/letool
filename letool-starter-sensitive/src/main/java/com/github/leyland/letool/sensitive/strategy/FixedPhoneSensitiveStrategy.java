package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 固话脱敏 —— 保留区号部分 + 后 4 位，中间用 {@code *} 填充.
 *
 * <pre>
 *   "010-12345678"  → "010-****5678"  （有 '-' 分隔符：区号完整保留）
 *   "02187654321"   → "021****4321"   （无分隔符：按比例估算区号位置）
 *   "010-12"        → "010-12"        （长度不足 5 位不处理）
 * </pre>
 *
 * <p>优先检测 {@code -} 分隔符来判断区号边界.
 * 无分隔符时，区号长度按 {@code 总长度/3} 估算（适用于 3~4 位区号）.
 * 可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class FixedPhoneSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        // 长度不足 5 位的不处理
        if (value == null || value.length() < 5) return value;

        char ch = context != null ? context.getMaskChar() : '*';

        // 分支一：有 '-' 分隔符（如 "010-12345678"）
        int dashIndex = value.indexOf('-');
        if (dashIndex > 0 && dashIndex < value.length() - 4) {
            String prefix = value.substring(0, dashIndex + 1);   // "010-"（区号+分隔符完整保留）
            String rest = value.substring(dashIndex + 1);        // "12345678"（号码部分）
            if (rest.length() <= 4) return value;                // 号码太短不处理
            // 号码部分：前段遮盖 + 后 4 位保留
            String masked = String.valueOf(ch).repeat(rest.length() - 4) + rest.substring(rest.length() - 4);
            return prefix + masked;
        }

        // 分支二：无分隔符（如 "02187654321"），按比例估算区号长度
        int prefix = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : value.length() / 3;
        if (value.length() <= prefix + 4) return value;
        return value.substring(0, prefix) + String.valueOf(ch).repeat(value.length() - prefix - 4)
                + value.substring(value.length() - 4);
    }
}
