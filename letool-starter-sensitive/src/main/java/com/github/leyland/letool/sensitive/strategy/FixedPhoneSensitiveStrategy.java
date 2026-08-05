package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 固话脱敏 —— 保留区号部分 + 后 4 位，中间用 {@code *} 填充.
 *
 * <pre>
 *   "010-12345678"  → "010-****5678"  （有 '-' 分隔符：区号完整保留）
 *   "02187654321"   → "021****4321"   （无分隔符：按比例估算区号位置）
 *   "010-12"        → "010-**"
 * </pre>
 *
 * <p>优先检测 {@code -} 分隔符来判断区号边界.
 * 无分隔符时，区号长度按 {@code 总长度/3} 估算（适用于 3~4 位区号）.
 * 可通过 {@link MaskContext} 覆盖默认保留长度和遮盖字符.</p>
 */
public class FixedPhoneSensitiveStrategy implements SensitiveStrategy<MaskContext> {

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
        char maskChar = MaskingSupport.maskChar(context);

        // 分支一：有 '-' 分隔符（如 "010-12345678"）
        int dashIndex = value.indexOf('-');
        if (dashIndex > 0 && dashIndex < value.length() - 1) {
            String areaCode = value.substring(0, dashIndex + 1);
            String number = value.substring(dashIndex + 1);
            return areaCode + MaskingSupport.maskMiddle(number, 0, 4, maskChar);
        }

        // 分支二：无分隔符（如 "02187654321"），按比例估算区号长度
        int prefix = MaskingSupport.keepPrefix(context, Math.max(1, value.length() / 3));
        return MaskingSupport.maskMiddle(value, prefix, 4, maskChar);
    }
}
