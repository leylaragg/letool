package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.style.FontWeight;

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

    /** 当前字体文件对应的字重。 */
    private final FontWeight weight;
    /** 当前字体文件的真实姿态，不依赖渲染器合成斜体。 */
    private final io.github.leylaragg.letool.print.document.style.FontPosture posture;

    /** 每次渲染打开字体流的供应器。 */
    private final Supplier<InputStream> streamSupplier;

    /** 当前字体族是否承担默认回退。 */
    private final boolean fallbackFamily;
    /** 是否只允许显式按字体族名称引用。 */
    private final boolean namedOnly;

    /**
     * 创建字体定义。
     *
     * @param familyName 字体族名称
     * @param weight 当前字体面对应的字重
     * @param streamSupplier 每次调用返回新字体流的供应器
     * @param fallbackFamily 是否把当前字体族作为默认回退族
     */
    public PdfFont(
            String familyName,
            FontWeight weight,
            Supplier<InputStream> streamSupplier,
            boolean fallbackFamily) {
        this(familyName, weight, io.github.leylaragg.letool.print.document.style.FontPosture.NORMAL, streamSupplier,
            fallbackFamily);
    }

    /**
     * 创建带真实字体姿态的字体定义。
     *
     * @param familyName 字体族名称
     * @param weight 字体文件对应的字重
     * @param posture 字体文件对应的姿态
     * @param streamSupplier 每次调用返回新字体流的供应器，消费方负责关闭
     * @param fallbackFamily 是否把当前字体族作为默认回退族
     * @throws NullPointerException 字重、姿态或供应器为 null
     * @throws IllegalArgumentException 字体族名称不合法
     */
    public PdfFont(String familyName, FontWeight weight,
        io.github.leylaragg.letool.print.document.style.FontPosture posture,
        Supplier<InputStream> streamSupplier, boolean fallbackFamily) {
        this(familyName, weight, posture, streamSupplier, fallbackFamily, false);
    }

    private PdfFont(String familyName, FontWeight weight,
        io.github.leylaragg.letool.print.document.style.FontPosture posture,
        Supplier<InputStream> streamSupplier, boolean fallbackFamily, boolean namedOnly) {
        this.namedOnly = namedOnly;
        this.posture = Objects.requireNonNull(posture, "posture 不能为空");
        this.familyName = normalizeFamilyName(familyName);
        this.weight = Objects.requireNonNull(weight, "weight 不能为空");
        this.streamSupplier = Objects.requireNonNull(streamSupplier, "streamSupplier 不能为空");
        this.fallbackFamily = fallbackFamily;
    }

    /**
     * 注册仅由模板明确引用的字体面，不改变现有默认字体顺序。
     *
     * @param familyName 字体族名称
     * @param weight 字体文件对应的字重
     * @param posture 字体文件对应的姿态
     * @param supplier 每次调用返回新字体流的供应器，消费方负责关闭
     * @return 仅显式引用的不可变字体定义
     * @throws NullPointerException 字重、姿态或供应器为 null
     * @throws IllegalArgumentException 字体族名称不合法
     */
    public static PdfFont named(String familyName, FontWeight weight,
        io.github.leylaragg.letool.print.document.style.FontPosture posture, Supplier<InputStream> supplier) {
        return new PdfFont(familyName, weight, posture, supplier, false, true);
    }

    /** @return 是否只允许显式按字体族名称引用 */
    public boolean namedOnly() {
        return namedOnly;
    }

    /** @return 规范化后的字体族名称 */
    public String familyName() {
        return familyName;
    }

    /** @return 当前字体面的字重 */
    public FontWeight weight() {
        return weight;
    }

    /** @return 当前字体文件的真实姿态 */
    public io.github.leylaragg.letool.print.document.style.FontPosture posture() {
        return posture;
    }

    /** @return 当前字体族是否承担默认回退 */
    public boolean fallbackFamily() {
        return fallbackFamily;
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
