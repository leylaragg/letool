package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.exception.PrintResourceException;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 真实解析 PDF 字体资源，并按需检查回退链的 Unicode 覆盖。
 *
 * @author leyland
 */
public final class PdfFontResourceValidator {

    /** 启动探测只接受一段可控规模的普通文本。 */
    private static final int MAX_PROBE_CODE_POINTS = 4_096;

    /** 本次需要检查的不可变字体目录。 */
    private final PdfFontCatalog catalog;

    /**
     * 创建字体资源校验器。
     *
     * @param catalog 已完成元数据校验的字体目录
     */
    public PdfFontResourceValidator(PdfFontCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog 不能为空");
    }

    /**
     * 解析全部字体，并检查探测文本是否被字体目录完整覆盖。
     *
     * @param probeText 可为空的 Unicode 探测文本
     */
    public void validate(String probeText) {
        String checkedText = validateProbeText(probeText);
        List<ParsedFont> parsedFonts = inspectFonts();
        RuntimeException validationFailure = null;
        try {
            checkedText.codePoints().forEach(codePoint -> requireCoverage(parsedFonts, codePoint));
        } catch (RuntimeException exception) {
            validationFailure = exception;
            throw exception;
        } finally {
            IOException closeFailure = closeFonts(parsedFonts);
            if (closeFailure != null) {
                if (validationFailure != null) {
                    validationFailure.addSuppressed(closeFailure);
                } else {
                    throw PrintResourceException.unavailable("pdf-font", closeFailure);
                }
            }
        }
    }

    /** 先限制探测文本，再接触可能昂贵的字体资源。 */
    private String validateProbeText(String probeText) {
        if (probeText == null) {
            throw new IllegalArgumentException("字体探测文本不能为空");
        }
        int codePointCount = probeText.codePointCount(0, probeText.length());
        if (codePointCount > MAX_PROBE_CODE_POINTS
                || probeText.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("字体探测文本格式不合法");
        }
        return probeText;
    }

    /** 每个字体面都必须能被真实解析并提供 Unicode cmap。 */
    private List<ParsedFont> inspectFonts() {
        List<ParsedFont> parsedFonts = new ArrayList<>();
        try {
            for (PdfFont font : catalog.fonts()) {
                parsedFonts.add(parse(font));
            }
            return parsedFonts;
        } catch (RuntimeException exception) {
            IOException closeFailure = closeFonts(parsedFonts);
            if (closeFailure != null) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    /** 先按 TrueType 解析，失败后再用 OpenType 解析同一字体资源。 */
    private ParsedFont parse(PdfFont font) {
        IOException trueTypeFailure;
        try (InputStream input = font.openStream()) {
            TrueTypeFont parsed = new TTFParser().parseEmbedded(input);
            return new ParsedFont(parsed, parsed.getUnicodeCmapLookup());
        } catch (IOException exception) {
            trueTypeFailure = exception;
        } catch (RuntimeException exception) {
            throw PrintResourceException.unavailable("pdf-font", exception);
        }

        try (InputStream input = font.openStream();
                RandomAccessReadBuffer data = new RandomAccessReadBuffer(input)) {
            TrueTypeFont parsed = new OTFParser().parse(data);
            return new ParsedFont(parsed, parsed.getUnicodeCmapLookup());
        } catch (IOException exception) {
            exception.addSuppressed(trueTypeFailure);
            throw PrintResourceException.unavailable("pdf-font", exception);
        } catch (RuntimeException exception) {
            exception.addSuppressed(trueTypeFailure);
            throw PrintResourceException.unavailable("pdf-font", exception);
        }
    }

    /** 当前码点只要被一个已解析字体覆盖即可继续。 */
    private void requireCoverage(List<ParsedFont> parsedFonts, int codePoint) {
        boolean covered = parsedFonts.stream().anyMatch(font -> font.contains(codePoint));
        if (!covered) {
            throw PrintResourceException.unavailable(
                    "startup.font-probe-text",
                    new IllegalStateException("字体回退链缺少字符覆盖"));
        }
    }

    /** 逆序关闭已解析字体，避免异常路径遗留 FontBox 资源。 */
    private IOException closeFonts(List<ParsedFont> parsedFonts) {
        IOException closeFailure = null;
        for (int index = parsedFonts.size() - 1; index >= 0; index--) {
            try {
                parsedFonts.get(index).close();
            } catch (IOException exception) {
                if (closeFailure == null) {
                    closeFailure = exception;
                } else {
                    closeFailure.addSuppressed(exception);
                }
            }
        }
        return closeFailure;
    }

    /** 保存解析后的字体及其 Unicode 字符映射。 */
    private static final class ParsedFont implements AutoCloseable {

        /** 需要在检查结束后关闭的 FontBox 字体。 */
        private final TrueTypeFont font;

        /** 当前字体的 Unicode 到字形映射。 */
        private final CmapLookup cmap;

        /** 保存同一次解析得到的字体和映射。 */
        private ParsedFont(TrueTypeFont font, CmapLookup cmap) {
            this.font = font;
            this.cmap = cmap;
        }

        /** @return 当前字体是否包含指定码点 */
        private boolean contains(int codePoint) {
            return cmap != null && cmap.getGlyphId(codePoint) != 0;
        }

        /** 释放 FontBox 持有的字体数据。 */
        @Override
        public void close() throws IOException {
            font.close();
        }
    }
}
