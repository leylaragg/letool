package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

/**
 * 经纬度脱敏 —— 保留整数部分，末 3 位小数用 {@code ***} 遮盖.
 *
 * <pre>
 *   "39.904200,116.407400" → "39.9***,116.4***"
 *   "31.230416"             → "31.2***"
 * </pre>
 *
 * <p>经纬度用逗号分隔，每段独立处理：保留到小数点后 1 位，其余小数位遮盖.
 * 替换字符串由 {@link MaskContext#getReplacement()} 控制（默认 "***"）.</p>
 */
public class PositionSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // 按逗号分割经纬度
        String[] parts = value.contains(",") ? value.split(",") : new String[]{value};
        String replacement = context != null && !context.getReplacement().equals("*") ? context.getReplacement() : "***";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(',');
            String part = parts[i].trim();
            if (part.length() > 4) {
                // 保留到小数点后 1 位，其余小数位 → ***
                sb.append(part, 0, part.length() - 3).append(replacement);
            } else {
                sb.append(part);  // 太短不处理
            }
        }
        return sb.toString();
    }
}
