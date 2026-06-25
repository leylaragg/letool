package com.github.leyland.letool.sensitive.core;

import com.github.leyland.letool.sensitive.annotation.Sensitive;

/**
 * 脱敏上下文 —— 包装单次脱敏操作所需的所有配置参数.
 *
 * <h3>字段语义</h3>
 * <pre>
 *   pattern       自定义正则表达式（SensitiveType.CUSTOM 时生效），匹配的部分将被 replacement 替换
 *   replacement   替换字符串（SensitiveType.CUSTOM 时生效），替换掉 pattern 匹配到的内容
 *   keepPrefix    保留前缀长度，-1 表示使用策略默认值。例如手机号 keepPrefix=3 → "138****5678"
 *   keepSuffix    保留后缀长度，-1 表示使用策略默认值。例如身份证 keepSuffix=4 → "3201****1234"
 *   maskChar      遮盖字符，默认 '*'。例如银行卡 maskChar='#' → "6222####7890"
 * </pre>
 *
 * <h3>构造方式</h3>
 * <ul>
 *   <li>{@link #DEFAULT} —— 全部使用策略默认值的静态单例</li>
 *   <li>{@link #from(Sensitive)} —— 从 @Sensitive 注解提取配置</li>
 *   <li>{@code new MaskContext().withKeepPrefix(3).withMaskChar('#')} —— Builder 链式风格</li>
 * </ul>
 */
public class MaskContext {

    /** 所有字段使用策略默认值的单例（避免频繁创建） */
    public static final MaskContext DEFAULT = new MaskContext();

    /**
     * 自定义正则表达式 —— 仅 CUSTOM 类型时生效。
     * 例如 {@code "(?<=工号)\\d{4}"} 匹配 "工号" 后面的 4 位数字。
     */
    private String pattern;

    /**
     * 替换字符串 —— pattern 匹配到的内容会被替换为此字符串。
     * 例如 {@code "****"} 将匹配内容替换为四个星号。
     */
    private String replacement = "*";

    /**
     * 保留前缀长度 —— 值为 -1 时表示使用策略内置的默认前缀长度。
     * 例如 {@code keepPrefix=3} 手机号 "13812345678" → "138****5678"（保留前 3 位）。
     */
    private int keepPrefix = -1;

    /**
     * 保留后缀长度 —— 值为 -1 时表示使用策略内置的默认后缀长度。
     * 例如 {@code keepSuffix=4} 身份证 "3201**********1234"（保留后 4 位）。
     */
    private int keepSuffix = -1;

    /**
     * 遮盖字符 —— 覆盖策略默认的遮盖字符，默认 '*'。
     * 例如设置为 '#' 时银行卡 "6222####7890"。
     */
    private char maskChar = '*';

    public MaskContext() {}

    /**
     * 从 @Sensitive 注解提取配置构造 MaskContext ——
     * pattern 为空字符串时设为 null（表示不使用自定义正则），其余字段直接映射。
     */
    public static MaskContext from(Sensitive annotation) {
        MaskContext ctx = new MaskContext();
        ctx.pattern = annotation.pattern().isEmpty() ? null : annotation.pattern();
        ctx.replacement = annotation.replacement();
        ctx.keepPrefix = annotation.keepPrefix();
        ctx.keepSuffix = annotation.keepSuffix();
        ctx.maskChar = annotation.maskChar();
        return ctx;
    }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public String getReplacement() { return replacement; }
    public void setReplacement(String replacement) { this.replacement = replacement; }
    public int getKeepPrefix() { return keepPrefix; }
    public void setKeepPrefix(int keepPrefix) { this.keepPrefix = keepPrefix; }
    public int getKeepSuffix() { return keepSuffix; }
    public void setKeepSuffix(int keepSuffix) { this.keepSuffix = keepSuffix; }
    public char getMaskChar() { return maskChar; }
    public void setMaskChar(char maskChar) { this.maskChar = maskChar; }

    /** 链式设置保留前缀长度 */
    public MaskContext withKeepPrefix(int keepPrefix) { this.keepPrefix = keepPrefix; return this; }
    /** 链式设置保留后缀长度 */
    public MaskContext withKeepSuffix(int keepSuffix) { this.keepSuffix = keepSuffix; return this; }
    /** 链式设置遮盖字符 */
    public MaskContext withMaskChar(char maskChar) { this.maskChar = maskChar; return this; }
    /** 链式设置自定义正则 */
    public MaskContext withPattern(String pattern) { this.pattern = pattern; return this; }
    /** 链式设置替换字符串 */
    public MaskContext withReplacement(String replacement) { this.replacement = replacement; return this; }
}
