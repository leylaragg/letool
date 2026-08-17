package io.github.leylaragg.letool.sensitive.strategy;

import io.github.leylaragg.letool.sensitive.core.MaskContext;

/**
 * 内置脱敏策略共享的安全遮盖方法。
 */
final class MaskingSupport {

    /**
     * 禁止创建共享工具类实例。
     */
    private MaskingSupport() {
    }

    /**
     * 获取有效的前缀保留长度。
     *
     * @param context 脱敏上下文，可为 {@code null}
     * @param defaultValue 策略默认值
     * @return 有效前缀保留长度
     */
    static int keepPrefix(MaskContext context, int defaultValue) {
        return context == null || context.getKeepPrefix() < 0
                ? defaultValue
                : context.getKeepPrefix();
    }

    /**
     * 获取有效的后缀保留长度。
     *
     * @param context 脱敏上下文，可为 {@code null}
     * @param defaultValue 策略默认值
     * @return 有效后缀保留长度
     */
    static int keepSuffix(MaskContext context, int defaultValue) {
        return context == null || context.getKeepSuffix() < 0
                ? defaultValue
                : context.getKeepSuffix();
    }

    /**
     * 获取有效遮盖字符。
     *
     * @param context 脱敏上下文，可为 {@code null}
     * @return 遮盖字符
     */
    static char maskChar(MaskContext context) {
        return context == null ? '*' : context.getMaskChar();
    }

    /**
     * 获取有效替换字符串。
     *
     * @param context 脱敏上下文，可为 {@code null}
     * @param defaultValue 策略默认替换字符串
     * @return 有效替换字符串
     */
    static String replacement(MaskContext context, String defaultValue) {
        if (context == null || context.getReplacement() == null) {
            return defaultValue;
        }
        return context.getReplacement();
    }

    /**
     * 完整遮盖非空字符串。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param maskChar 遮盖字符
     * @return 完整遮盖结果；空值保持不变
     */
    static String maskAll(String value, char maskChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return String.valueOf(maskChar).repeat(value.length());
    }

    /**
     * 保留首尾并遮盖中间内容。
     *
     * <p>当原始值不足以同时保留首尾时，完整遮盖原始值，禁止返回明文。</p>
     *
     * @param value 原始字符串，可为 {@code null}
     * @param prefix 前缀保留长度
     * @param suffix 后缀保留长度
     * @param maskChar 遮盖字符
     * @return 安全遮盖结果
     */
    static String maskMiddle(String value, int prefix, int suffix, char maskChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= prefix + suffix) {
            return maskAll(value, maskChar);
        }
        return value.substring(0, prefix)
                + String.valueOf(maskChar).repeat(value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }
}
