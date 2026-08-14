package com.github.leyland.letool.exception.support;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * 不依赖应用上下文，安全格式化异常默认消息。
 */
public final class MessageFormatter {

    private MessageFormatter() {
        throw new AssertionError("MessageFormatter must not be instantiated");
    }

    /**
     * 按指定语言环境格式化消息模板，并在模板格式错误时安全兜底。
     *
     * @param pattern 必填的 {@link MessageFormat} 模板
     * @param locale 格式化语言环境；传 {@code null} 时使用 {@link Locale#ROOT}
     * @param arguments 填充模板的参数；数组会进行防御性复制
     * @return 没有参数时返回原始模板；模板合法时返回格式化消息；
     *         模板非法时返回原始模板和参数列表
     * @throws NullPointerException 当 {@code pattern} 为 {@code null} 时抛出
     */
    public static String format(String pattern, Locale locale, Object... arguments) {
        Objects.requireNonNull(pattern, "pattern");
        Locale effectiveLocale = locale == null ? Locale.ROOT : locale;
        Object[] safeArguments = arguments == null ? new Object[0] : arguments.clone();
        if (safeArguments.length == 0) {
            return pattern;
        }

        try {
            return new MessageFormat(pattern, effectiveLocale).format(safeArguments);
        } catch (IllegalArgumentException invalidPattern) {
            // 即使资源模板格式错误，异常构造也必须保留原始故障信息。
            return pattern + " " + Arrays.toString(safeArguments);
        }
    }
}
