package io.github.leylaragg.letool.sensitive.strategy;

import io.github.leylaragg.letool.sensitive.core.MaskContext;
import io.github.leylaragg.letool.sensitive.core.SensitiveStrategy;

/**
 * 邮箱脱敏 —— 保留用户名首字 + 域名，中间部分用 {@code *} 遮盖.
 *
 * <pre>
 *   "test@example.com"    → "t***@example.com"
 *   "hello123@example.com" → "h***@example.com"
 *   "ab@example.com"       → "a*@example.com"（用户名短于等于 2 位时保留首字 + 遮盖剩余）
 *   "a@example.com"        → "*@example.com"
 * </pre>
 *
 * <p>可通过 {@link MaskContext#getMaskChar()} 覆盖默认遮盖字符.</p>
 */
public class EmailSensitiveStrategy implements SensitiveStrategy<MaskContext> {

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
        int atIndex = value.indexOf('@');
        if (atIndex <= 0 || atIndex != value.lastIndexOf('@') || atIndex == value.length() - 1) {
            return MaskingSupport.maskAll(value, maskChar);
        }

        String namePart = value.substring(0, atIndex);
        String domain = value.substring(atIndex);
        if (namePart.length() == 1) {
            return maskChar + domain;
        }

        int maskLength = Math.min(3, namePart.length() - 1);
        String maskedName = namePart.charAt(0) + String.valueOf(maskChar).repeat(maskLength);
        return maskedName + domain;
    }
}
