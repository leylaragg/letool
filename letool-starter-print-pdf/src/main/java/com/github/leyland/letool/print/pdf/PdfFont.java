package com.github.leyland.letool.print.pdf;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 宿主交给 PDF 渲染器使用的不可变字体定义。
 *
 * <p>字体文件及许可证由宿主管理，供应器应为每次调用打开一个新的输入流。</p>
 *
 * @author leyland
 */
public final class PdfFont {

    /** 允许安全进入框架 CSS 的字体族名称。 */
    private static final Pattern FAMILY_NAME = Pattern.compile(
            "[\\p{L}\\p{N}][\\p{L}\\p{N} _-]{0,127}");

    /** 规范化后的字体族名称。 */
    private final String familyName;

    /** 每次渲染打开字体流的供应器。 */
    private final Supplier<InputStream> streamSupplier;

    /** 是否作为字体列表中的最终回退项。 */
    private final boolean fallback;

    /**
     * 创建字体定义。
     *
     * @param familyName 字体族名称
     * @param streamSupplier 每次调用返回新字体流的供应器
     * @param fallback 是否作为最终回退字体
     */
    public PdfFont(
            String familyName,
            Supplier<InputStream> streamSupplier,
            boolean fallback) {
        this.familyName = normalizeFamilyName(familyName);
        this.streamSupplier = Objects.requireNonNull(streamSupplier, "streamSupplier 不能为空");
        this.fallback = fallback;
    }

    /** @return 规范化后的字体族名称 */
    public String familyName() {
        return familyName;
    }

    /** @return 是否作为最终回退字体 */
    public boolean fallback() {
        return fallback;
    }

    /**
     * 为一次渲染打开字体流。
     *
     * @return 由调用方负责交给渲染库并关闭的字体流
     * @throws IllegalStateException 供应器返回 {@code null} 时抛出
     */
    public InputStream openStream() {
        InputStream stream = streamSupplier.get();
        if (stream == null) {
            throw new IllegalStateException("字体流不能为空");
        }
        return stream;
    }

    /** 规范化并限制字体族，避免宿主配置改变 CSS 结构。 */
    private static String normalizeFamilyName(String familyName) {
        if (familyName == null) {
            throw new IllegalArgumentException("字体族不能为空");
        }
        String normalized = familyName.trim();
        if (!FAMILY_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("字体族格式不合法");
        }
        return normalized;
    }
}
