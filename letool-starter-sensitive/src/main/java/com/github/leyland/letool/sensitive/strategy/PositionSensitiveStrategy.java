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

        String[] parts = value.split(",", -1);
        if (parts.length > 2) {
            return MaskingSupport.maskAll(value, MaskingSupport.maskChar(context));
        }
        String replacement = MaskingSupport.replacement(context, "***");

        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                masked.append(',');
            }
            String part = parts[i].trim();
            if (!part.matches("[-+]?\\d{1,3}(\\.\\d+)?")) {
                return MaskingSupport.maskAll(value, MaskingSupport.maskChar(context));
            }
            masked.append(maskCoordinate(part, replacement, MaskingSupport.maskChar(context)));
        }
        return masked.toString();
    }

    /**
     * 保留坐标整数部分和首位小数，并遮盖剩余精度。
     *
     * @param coordinate 单个经度或纬度
     * @param replacement 精度替换字符串
     * @param maskChar 完全遮盖时使用的字符
     * @return 脱敏后的单个坐标
     */
    private static String maskCoordinate(String coordinate, String replacement, char maskChar) {
        int decimalPoint = coordinate.indexOf('.');
        if (decimalPoint < 0) {
            return MaskingSupport.maskAll(coordinate, maskChar);
        }
        String integerPart = coordinate.substring(0, decimalPoint);
        String decimalPart = coordinate.substring(decimalPoint + 1);
        if (decimalPart.length() == 1) {
            return integerPart + "." + replacement;
        }
        return integerPart + "." + decimalPart.charAt(0) + replacement;
    }
}
