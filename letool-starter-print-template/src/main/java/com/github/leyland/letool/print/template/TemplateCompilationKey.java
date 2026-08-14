package com.github.leyland.letool.print.template;

import com.github.leyland.letool.print.api.OutputFormat;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 锁定一次模板编译所依赖的全部稳定条件。
 *
 * <p>该值对象不可变且线程安全，渲染器不必了解具体缓存实现。</p>
 *
 * @author leyland
 */
public final class TemplateCompilationKey {

    /** 集合摘要统一使用小写 SHA-256 十六进制文本。 */
    private static final Pattern DIGEST_PATTERN = Pattern.compile("[0-9a-f]{64}");

    /** 模板集合版本。 */
    private final long templateSetVersion;

    /** 模板集合内容摘要。 */
    private final String templateSetDigest;

    /** 待编译的模板代码。 */
    private final String templateCode;

    /** 模板语言版本。 */
    private final int dslVersion;

    /** 只读上下文版本。 */
    private final int contextVersion;

    /** 渲染器配置版本。 */
    private final long rendererProfileVersion;

    /** 目标输出格式。 */
    private final OutputFormat outputFormat;

    /**
     * 创建完整的模板编译键。
     *
     * @param templateSetVersion 模板集合版本
     * @param templateSetDigest 模板集合 SHA-256 摘要
     * @param templateCode 稳定模板代码
     * @param dslVersion 模板语言版本
     * @param contextVersion 只读上下文版本
     * @param rendererProfileVersion 渲染器配置版本
     * @param outputFormat 目标输出格式
     * @throws IllegalArgumentException 版本、摘要或模板代码不符合稳定键约束时抛出
     * @throws NullPointerException 输出格式为空时抛出
     */
    public TemplateCompilationKey(
            long templateSetVersion,
            String templateSetDigest,
            String templateCode,
            int dslVersion,
            int contextVersion,
            long rendererProfileVersion,
            OutputFormat outputFormat) {
        this.templateSetVersion = requirePositive("templateSetVersion", templateSetVersion);
        this.templateSetDigest = requireDigest(templateSetDigest);
        this.templateCode = requireCode(templateCode);
        this.dslVersion = requirePositive("dslVersion", dslVersion);
        this.contextVersion = requirePositive("contextVersion", contextVersion);
        this.rendererProfileVersion = requirePositive("rendererProfileVersion", rendererProfileVersion);
        this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat 不能为空");
    }

    /** @return 模板集合版本 */
    public long templateSetVersion() {
        return templateSetVersion;
    }

    /** @return 模板集合内容摘要 */
    public String templateSetDigest() {
        return templateSetDigest;
    }

    /** @return 稳定模板代码 */
    public String templateCode() {
        return templateCode;
    }

    /** @return 模板语言版本 */
    public int dslVersion() {
        return dslVersion;
    }

    /** @return 只读上下文版本 */
    public int contextVersion() {
        return contextVersion;
    }

    /** @return 渲染器配置版本 */
    public long rendererProfileVersion() {
        return rendererProfileVersion;
    }

    /** @return 目标输出格式 */
    public OutputFormat outputFormat() {
        return outputFormat;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TemplateCompilationKey that)) {
            return false;
        }
        return templateSetVersion == that.templateSetVersion
                && dslVersion == that.dslVersion
                && contextVersion == that.contextVersion
                && rendererProfileVersion == that.rendererProfileVersion
                && templateSetDigest.equals(that.templateSetDigest)
                && templateCode.equals(that.templateCode)
                && outputFormat.equals(that.outputFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateSetVersion, templateSetDigest, templateCode, dslVersion,
                contextVersion, rendererProfileVersion, outputFormat);
    }

    @Override
    public String toString() {
        return "TemplateCompilationKey[templateSetVersion=" + templateSetVersion
                + ", templateSetDigest=" + templateSetDigest
                + ", templateCode=" + templateCode
                + ", dslVersion=" + dslVersion
                + ", contextVersion=" + contextVersion
                + ", rendererProfileVersion=" + rendererProfileVersion
                + ", outputFormat=" + outputFormat + "]";
    }

    /**
     * 校验模板集合摘要。
     *
     * @param digest 待校验摘要
     * @return 可用于缓存键的摘要
     */
    private static String requireDigest(String digest) {
        if (digest == null || !DIGEST_PATTERN.matcher(digest).matches()) {
            throw new IllegalArgumentException("templateSetDigest 必须为小写 SHA-256 十六进制文本");
        }
        return digest;
    }

    /**
     * 校验模板代码并保留宿主给出的稳定值。
     *
     * @param code 待校验模板代码
     * @return 可用于缓存键的模板代码
     */
    private static String requireCode(String code) {
        if (code == null || code.isBlank() || code.length() > 128) {
            throw new IllegalArgumentException("templateCode 必须为不超过 128 个字符的非空白文本");
        }
        return code;
    }

    /**
     * 校验正整数版本。
     *
     * @param name 参数名
     * @param value 参数值
     * @return 已校验参数值
     */
    private static int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须为正整数");
        }
        return value;
    }

    /**
     * 校验正长整数版本。
     *
     * @param name 参数名
     * @param value 参数值
     * @return 已校验参数值
     */
    private static long requirePositive(String name, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须为正整数");
        }
        return value;
    }
}
