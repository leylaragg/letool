package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * IPv6 脱敏 —— 保留前若干段 + 末段，中间段用 {@code ****} 替换.
 *
 * <pre>
 *   "2001:0db8:85a3:0000:0000:8a2e:0370:7334" → "2001:****::7334"
 *   "fe80::1"                                    → "fe80::1"（不足 3 段不处理）
 * </pre>
 *
 * <p>保留段数由 {@link MaskContext#getKeepPrefix()} 控制（默认 1 段），
 * 替换字符串由 {@link MaskContext#getReplacement()} 控制（默认 "****"）.
 * 末段始终保留不遮盖.</p>
 */
public class Ipv6SensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // 按 ':' 分割（保留空段，处理 :: 缩写）
        String[] parts = value.split(":", -1);
        // 不足 3 段的不处理（无法有效遮盖中间部分）
        if (parts.length < 3) return value;

        int keepCount = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : 1;
        String replacement = context != null && !context.getReplacement().equals("*") ? context.getReplacement() : "****";

        StringBuilder sb = new StringBuilder();
        boolean skipped = false;  // 标记中间段是否已替换（合并多个中间段为一个 "****"）
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(':');
            if (i < keepCount) {
                sb.append(parts[i]);             // 前段：保持原值
            } else if (i == parts.length - 1) {
                sb.append(parts[i]);             // 末段：保持原值（不遮盖）
            } else if (!skipped) {
                sb.append(replacement);          // 中间段：替换为 "****"（仅一次）
                skipped = true;
            }
        }
        return sb.toString();
    }
}
