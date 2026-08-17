package io.github.leylaragg.letool.sensitive.core;

import io.github.leylaragg.letool.sensitive.annotation.Sensitive;
import io.github.leylaragg.letool.sensitive.exception.SensitiveException;

/**
 * 单次脱敏操作使用的不可变上下文。
 *
 * <p>{@code -1} 表示沿用策略默认保留长度，{@code 0} 表示不保留对应方向的字符。
 * 所有链式方法都会返回新对象，因此共享的 {@link #DEFAULT} 不会被调用方修改。</p>
 */
public final class MaskContext {

    /** 使用全部策略默认参数的共享上下文。 */
    public static final MaskContext DEFAULT = new MaskContext(null, null, -1, -1, '*');

    private final String pattern;
    private final String replacement;
    private final int keepPrefix;
    private final int keepSuffix;
    private final char maskChar;

    /**
     * 创建使用策略默认参数的上下文。
     */
    public MaskContext() {
        this(null, null, -1, -1, '*');
    }

    /**
     * 创建不可变脱敏上下文。
     *
     * @param pattern 自定义正则表达式，可为 {@code null}
     * @param replacement 正则替换字符串，可为 {@code null} 以使用策略默认值
     * @param keepPrefix 前缀保留长度
     * @param keepSuffix 后缀保留长度
     * @param maskChar 遮盖字符
     */
    private MaskContext(
            String pattern,
            String replacement,
            int keepPrefix,
            int keepSuffix,
            char maskChar) {
        this.pattern = normalizePattern(pattern);
        this.replacement = replacement;
        this.keepPrefix = requireKeepLength("keepPrefix", keepPrefix);
        this.keepSuffix = requireKeepLength("keepSuffix", keepSuffix);
        this.maskChar = maskChar;
    }

    /**
     * 从字段注解创建不可变上下文。
     *
     * @param annotation 脱敏字段注解
     * @return 与注解参数一致的脱敏上下文
     */
    public static MaskContext from(Sensitive annotation) {
        if (annotation == null) {
            throw SensitiveException.configurationInvalid("Sensitive 注解不能为空");
        }
        return new MaskContext(
                annotation.pattern(),
                annotation.replacement().isEmpty() ? null : annotation.replacement(),
                annotation.keepPrefix(),
                annotation.keepSuffix(),
                annotation.maskChar());
    }

    /**
     * 获取自定义正则表达式。
     *
     * @return 自定义正则表达式，未配置时返回 {@code null}
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * 获取正则替换字符串。
     *
     * @return 正则替换字符串，未配置时返回 {@code null}
     */
    public String getReplacement() {
        return replacement;
    }

    /**
     * 获取前缀保留长度。
     *
     * @return 前缀保留长度，{@code -1} 表示使用策略默认值
     */
    public int getKeepPrefix() {
        return keepPrefix;
    }

    /**
     * 获取后缀保留长度。
     *
     * @return 后缀保留长度，{@code -1} 表示使用策略默认值
     */
    public int getKeepSuffix() {
        return keepSuffix;
    }

    /**
     * 获取遮盖字符。
     *
     * @return 遮盖字符
     */
    public char getMaskChar() {
        return maskChar;
    }

    /**
     * 派生使用指定前缀保留长度的新上下文。
     *
     * @param keepPrefix 前缀保留长度，必须为 {@code -1} 或非负数
     * @return 新的不可变上下文
     */
    public MaskContext withKeepPrefix(int keepPrefix) {
        return new MaskContext(pattern, replacement, keepPrefix, keepSuffix, maskChar);
    }

    /**
     * 派生使用指定后缀保留长度的新上下文。
     *
     * @param keepSuffix 后缀保留长度，必须为 {@code -1} 或非负数
     * @return 新的不可变上下文
     */
    public MaskContext withKeepSuffix(int keepSuffix) {
        return new MaskContext(pattern, replacement, keepPrefix, keepSuffix, maskChar);
    }

    /**
     * 派生使用指定遮盖字符的新上下文。
     *
     * @param maskChar 遮盖字符
     * @return 新的不可变上下文
     */
    public MaskContext withMaskChar(char maskChar) {
        return new MaskContext(pattern, replacement, keepPrefix, keepSuffix, maskChar);
    }

    /**
     * 派生使用指定正则表达式的新上下文。
     *
     * @param pattern 自定义正则表达式，空白值表示不配置
     * @return 新的不可变上下文
     */
    public MaskContext withPattern(String pattern) {
        return new MaskContext(pattern, replacement, keepPrefix, keepSuffix, maskChar);
    }

    /**
     * 派生使用指定替换字符串的新上下文。
     *
     * @param replacement 正则替换字符串；为 {@code null} 时恢复策略默认值
     * @return 新的不可变上下文
     */
    public MaskContext withReplacement(String replacement) {
        return new MaskContext(pattern, replacement, keepPrefix, keepSuffix, maskChar);
    }

    /**
     * 规范化可选正则表达式。
     *
     * @param pattern 原始正则表达式
     * @return 规范化后的正则表达式
     */
    private static String normalizePattern(String pattern) {
        return pattern == null || pattern.isBlank() ? null : pattern;
    }

    /**
     * 校验保留长度。
     *
     * @param name 参数名称
     * @param value 参数值
     * @return 已校验的参数值
     */
    private static int requireKeepLength(String name, int value) {
        if (value < -1) {
            throw SensitiveException.configurationInvalid(name + " 必须为 -1 或非负数");
        }
        return value;
    }
}
