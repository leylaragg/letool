package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;
import com.github.leyland.letool.sensitive.exception.SensitiveException;

/**
 * IPv4 脱敏 —— 保留前若干段（默认前 2 段），其余段用 {@code *} 替换.
 *
 * <pre>
 *   "192.168.1.100" → "192.168.*.*"
 *   "10.0.0.1"       → "10.0.*.*"
 *   "172.16.30.50"   → "172.16.*.*"
 * </pre>
 *
 * <p>保留段数由 {@link MaskContext#getKeepPrefix()} 控制（默认 2 段），
 * 替换字符串由 {@link MaskContext#getReplacement()} 控制（默认 "*"）.</p>
 *
 * <p>输入不是合法 IPv4 格式时完整遮盖，禁止回退返回明文。</p>
 */
public class Ipv4SensitiveStrategy implements SensitiveStrategy<MaskContext> {

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

        String[] parts = value.split("\\.", -1);
        if (parts.length != 4 || !isValidAddress(parts)) {
            return MaskingSupport.maskAll(value, MaskingSupport.maskChar(context));
        }

        int keepCount = MaskingSupport.keepPrefix(context, 2);
        if (keepCount > 3) {
            throw SensitiveException.configurationInvalid("IPv4 最多保留前 3 段");
        }
        String replacement = MaskingSupport.replacement(context, "*");

        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                masked.append('.');
            }
            if (i < keepCount) {
                masked.append(parts[i]);
            } else {
                masked.append(replacement);
            }
        }
        return masked.toString();
    }

    /**
     * 校验 IPv4 的四个数值段。
     *
     * @param parts IPv4 分段
     * @return {@code true} 表示格式和范围合法
     */
    private static boolean isValidAddress(String[] parts) {
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            try {
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }
}
