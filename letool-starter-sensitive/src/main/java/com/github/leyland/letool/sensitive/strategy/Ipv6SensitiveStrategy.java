package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;
import com.github.leyland.letool.sensitive.exception.SensitiveException;

import java.util.Arrays;
import java.util.List;

/**
 * IPv6 脱敏 —— 保留前若干段 + 末段，中间段用 {@code ****} 替换.
 *
 * <pre>
 *   "2001:0db8:85a3:0000:0000:8a2e:0370:7334" → "2001:****:7334"
 *   "fe80::1"                                    → "*******"（无法隐藏中间段时完整遮盖）
 * </pre>
 *
 * <p>保留段数由 {@link MaskContext#getKeepPrefix()} 控制（默认 1 段），
 * 替换字符串由 {@link MaskContext#getReplacement()} 控制（默认 "****"）.
 * 末段始终保留不遮盖.</p>
 */
public class Ipv6SensitiveStrategy implements SensitiveStrategy<MaskContext> {

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
        if (!isValidAddress(value)) {
            return MaskingSupport.maskAll(value, MaskingSupport.maskChar(context));
        }

        List<String> visibleParts = Arrays.stream(value.split(":", -1))
                .filter(part -> !part.isEmpty())
                .toList();
        if (visibleParts.size() < 3) {
            return MaskingSupport.maskAll(value, MaskingSupport.maskChar(context));
        }

        int keepCount = MaskingSupport.keepPrefix(context, 1);
        if (keepCount > visibleParts.size() - 2) {
            throw SensitiveException.configurationInvalid("IPv6 必须至少遮盖一个中间段");
        }
        String replacement = MaskingSupport.replacement(context, "****");

        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < keepCount; i++) {
            if (i > 0) {
                masked.append(':');
            }
            masked.append(visibleParts.get(i));
        }
        if (!masked.isEmpty()) {
            masked.append(':');
        }
        return masked.append(replacement)
                .append(':')
                .append(visibleParts.get(visibleParts.size() - 1))
                .toString();
    }

    /**
     * 对常规或双冒号压缩形式进行基础格式校验。
     *
     * @param value IPv6 字符串
     * @return {@code true} 表示格式可安全识别
     */
    private static boolean isValidAddress(String value) {
        if (!value.contains(":")) {
            return false;
        }
        int compressionIndex = value.indexOf("::");
        if (compressionIndex >= 0 && compressionIndex != value.lastIndexOf("::")) {
            return false;
        }
        String[] parts = value.split(":", -1);
        int nonEmptyCount = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            nonEmptyCount++;
            if (!part.matches("[0-9a-fA-F]{1,4}")) {
                return false;
            }
        }
        return compressionIndex >= 0
                ? nonEmptyCount < 8
                : nonEmptyCount == 8;
    }
}
