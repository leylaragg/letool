package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 密码脱敏 —— 完全遮盖，不保留任何原始字符，固定长度输出.
 *
 * <pre>
 *   "abc123"     → "******"
 *   "myPa$$w0rd" → "********"（最长 8 个遮盖字符，不暴露密码长度）
 * </pre>
 *
 * <p>遮盖长度取 {@code min(原密码长度, 8)}，避免通过遮盖长度推断出密码长度.
 * 可通过 {@link MaskContext#getMaskChar()} 覆盖默认遮盖字符.</p>
 */
public class PasswordSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // context 为 null 时使用默认遮盖字符 '*'
        char ch = context != null ? context.getMaskChar() : '*';

        // 遮盖长度 = min(原长度, 8)，防止通过遮盖长度推断密码长度
        int len = Math.min(value.length(), 8);
        return String.valueOf(ch).repeat(len);
    }
}
