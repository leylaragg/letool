package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 邮箱脱敏 —— 保留用户名首字 + 域名，中间部分用 {@code *} 遮盖.
 *
 * <pre>
 *   "test@example.com"    → "t***@example.com"
 *   "hello123@example.com" → "h***@example.com"
 *   "ab@example.com"       → "a*@example.com"（用户名短于等于 2 位时保留首字 + 遮盖剩余）
 *   "a@example.com"        → "a@example.com"（@ 前只有 1 位，不处理）
 * </pre>
 *
 * <p>可通过 {@link MaskContext#getMaskChar()} 覆盖默认遮盖字符.</p>
 */
public class EmailSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // 定位 @ 符号位置
        int atIndex = value.indexOf('@');
        // @ 前不足 2 位的不处理（无法有效遮盖）
        if (atIndex <= 1) return value;

        char ch = context != null ? context.getMaskChar() : '*';
        String namePart = value.substring(0, atIndex);   // @ 前部分
        String domain = value.substring(atIndex);         // @ 及域名部分

        // 用户名部分的遮盖逻辑：保留首字，其余用 * 替换（最多 3 个 *）
        String maskedName;
        if (namePart.length() <= 2) {
            maskedName = namePart.charAt(0) + String.valueOf(ch).repeat(namePart.length() - 1);
        } else {
            maskedName = namePart.charAt(0) + String.valueOf(ch).repeat(Math.min(3, namePart.length() - 1));
        }
        return maskedName + domain;
    }
}
