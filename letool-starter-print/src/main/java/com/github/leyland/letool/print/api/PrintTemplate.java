package com.github.leyland.letool.print.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * 单次打印锁定的不可变模板快照。
 *
 * <p>模板内容在保存和返回时均进行复制，调用方不能修改已创建的快照。</p>
 *
 * @author leyland
 */
public final class PrintTemplate {

    /** 单个模板允许的最大字节数。 */
    public static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;

    /** 宿主系统使用的稳定模板代码。 */
    private final String templateCode;

    /** 决定顶层打印管线的模板格式。 */
    private final TemplateFormat templateFormat;

    /** 模板语言或源格式的契约版本。 */
    private final int dslVersion;

    /** 本次打印锁定的模板集合版本。 */
    private final long templateSetVersion;

    /** 模板要求的只读上下文版本。 */
    private final int contextVersion;

    /** 模板源内容的内部副本。 */
    private final byte[] content;

    /**
     * 创建不可变模板快照。
     *
     * @param templateCode 稳定模板代码，最长 128 个字符
     * @param templateFormat 模板格式
     * @param dslVersion 正整数模板语言版本
     * @param templateSetVersion 正整数模板集合版本
     * @param contextVersion 正整数上下文版本
     * @param content 非空模板内容，最大 2 MiB
     * @throws IllegalArgumentException 参数不满足模板快照契约时抛出
     */
    public PrintTemplate(
            String templateCode,
            TemplateFormat templateFormat,
            int dslVersion,
            long templateSetVersion,
            int contextVersion,
            byte[] content) {
        this.templateCode = requireCode(templateCode);
        this.templateFormat = Objects.requireNonNull(templateFormat, "templateFormat 不能为空");
        this.dslVersion = requirePositive("dslVersion", dslVersion);
        this.templateSetVersion = requirePositive("templateSetVersion", templateSetVersion);
        this.contextVersion = requirePositive("contextVersion", contextVersion);
        this.content = copyContent(content);
    }

    /** @return 稳定模板代码 */
    public String templateCode() {
        return templateCode;
    }

    /** @return 模板格式 */
    public TemplateFormat templateFormat() {
        return templateFormat;
    }

    /** @return 模板语言版本 */
    public int dslVersion() {
        return dslVersion;
    }

    /** @return 模板集合版本 */
    public long templateSetVersion() {
        return templateSetVersion;
    }

    /** @return 上下文契约版本 */
    public int contextVersion() {
        return contextVersion;
    }

    /** @return 模板内容的独立副本 */
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    /** 校验模板代码。 */
    private static String requireCode(String templateCode) {
        if (templateCode == null || templateCode.isBlank() || templateCode.length() > 128) {
            throw new IllegalArgumentException("templateCode 必须为不超过 128 个字符的非空白文本");
        }
        return templateCode;
    }

    /** 校验正整数参数。 */
    private static int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须为正整数");
        }
        return value;
    }

    /** 校验正长整数参数。 */
    private static long requirePositive(String name, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须为正整数");
        }
        return value;
    }

    /** 校验并复制模板内容。 */
    private static byte[] copyContent(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException("content 必须非空且不能超过 2 MiB");
        }
        return Arrays.copyOf(content, content.length);
    }
}
