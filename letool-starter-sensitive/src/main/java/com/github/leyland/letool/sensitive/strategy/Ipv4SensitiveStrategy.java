package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

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
 * <p>输入不是标准 4 段 IPv4 格式时原样返回.</p>
 */
public class Ipv4SensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // 按 '.' 分割为 4 段
        String[] parts = value.split("\\.");
        // 非标准 IPv4 格式 → 原样返回
        if (parts.length != 4) return value;

        // 保留前 keepCount 段（默认 2），其余段用 replacement 替换
        int keepCount = context != null && context.getKeepPrefix() > 0 ? context.getKeepPrefix() : 2;
        String replacement = context != null && !context.getReplacement().equals("*") ? context.getReplacement() : "*";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('.');
            if (i < keepCount) {
                sb.append(parts[i]);        // 保留段：保持原值
            } else {
                sb.append(replacement);     // 遮盖段：替换为 "*"
            }
        }
        return sb.toString();
    }
}
