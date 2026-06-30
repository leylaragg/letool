package com.github.leyland.letool.file.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTypeUtil 单元测试 —— 基于魔术数字的文件类型检测。
 *
 * @author leyland
 */
@DisplayName("FileTypeUtil 文件类型检测测试")
class FileTypeUtilTest {

    // ======================== 魔术数字字节序列 ========================

    // PNG: 89 50 4E 47
    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D};
    // JPEG: FF D8 FF
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};
    // GIF: 47 49 46 38
    private static final byte[] GIF_BYTES = {0x47, 0x49, 0x46, 0x38, 0x39};
    // BMP: 42 4D
    private static final byte[] BMP_BYTES = {0x42, 0x4D, 0x00, 0x00, 0x00};
    // PDF: 25 50 44 46 2D
    private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D};
    // ZIP (standard): 50 4B 03 04
    private static final byte[] ZIP_BYTES = {0x50, 0x4B, 0x03, 0x04, 0x14};
    // ZIP (empty archive): 50 4B 05 06
    private static final byte[] ZIP_EMPTY_BYTES = {0x50, 0x4B, 0x05, 0x06, 0x00};
    // ZIP (spanned archive): 50 4B 07 08
    private static final byte[] ZIP_SPANNED_BYTES = {0x50, 0x4B, 0x07, 0x08, 0x00};
    // RAR: 52 61 72 21
    private static final byte[] RAR_BYTES = {0x52, 0x61, 0x72, 0x21, 0x1A};
    // GZIP: 1F 8B 08
    private static final byte[] GZIP_BYTES = {(byte) 0x1F, (byte) 0x8B, 0x08, 0x00, 0x00};
    // MP3: 49 44 33
    private static final byte[] MP3_BYTES = {0x49, 0x44, 0x33, 0x03, 0x00};
    // Java CLASS: CA FE BA BE
    private static final byte[] CLASS_BYTES = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00};
    // DOC (OLE2): D0 CF 11 E0
    private static final byte[] DOC_BYTES = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1};
    // DOCX (ZIP-based but not matching ZIP entries): 50 4B 01 02 + extra bytes
    // 必须使用未注册在 MAGIC_MAP 中的 504B 变体，否则会先匹配到 ZIP 条目
    private static final byte[] DOCX_BYTES = {0x50, 0x4B, 0x01, 0x02, 0x14, 0x00, 0x06, 0x00};
    // XML: 3C 3F 78 6D 6C
    private static final byte[] XML_BYTES = {0x3C, 0x3F, 0x78, 0x6D, 0x6C};
    // Unknown bytes
    private static final byte[] UNKNOWN_BYTES = {0x00, 0x00, 0x00, 0x00, 0x00};

    // ======================== detect 测试 ========================

    @Nested
    @DisplayName("detect - 文件类型检测")
    class DetectTests {

        @Test
        @DisplayName("应检测 PNG 文件")
        void shouldDetectPng() {
            assertEquals("PNG", FileTypeUtil.detect(stream(PNG_BYTES)));
        }

        @Test
        @DisplayName("应检测 JPEG 文件")
        void shouldDetectJpeg() {
            assertEquals("JPEG", FileTypeUtil.detect(stream(JPEG_BYTES)));
        }

        @Test
        @DisplayName("应检测 GIF 文件")
        void shouldDetectGif() {
            assertEquals("GIF", FileTypeUtil.detect(stream(GIF_BYTES)));
        }

        @Test
        @DisplayName("应检测 BMP 文件")
        void shouldDetectBmp() {
            assertEquals("BMP", FileTypeUtil.detect(stream(BMP_BYTES)));
        }

        @Test
        @DisplayName("应检测 PDF 文件")
        void shouldDetectPdf() {
            assertEquals("PDF", FileTypeUtil.detect(stream(PDF_BYTES)));
        }

        @Test
        @DisplayName("应检测标准 ZIP 文件")
        void shouldDetectStandardZip() {
            assertEquals("ZIP", FileTypeUtil.detect(stream(ZIP_BYTES)));
        }

        @Test
        @DisplayName("应检测空 ZIP 归档文件")
        void shouldDetectEmptyZipArchive() {
            assertEquals("ZIP", FileTypeUtil.detect(stream(ZIP_EMPTY_BYTES)));
        }

        @Test
        @DisplayName("应检测跨卷 ZIP 归档文件")
        void shouldDetectSpannedZipArchive() {
            assertEquals("ZIP", FileTypeUtil.detect(stream(ZIP_SPANNED_BYTES)));
        }

        @Test
        @DisplayName("应检测 RAR 文件")
        void shouldDetectRar() {
            assertEquals("RAR", FileTypeUtil.detect(stream(RAR_BYTES)));
        }

        @Test
        @DisplayName("应检测 GZIP 文件")
        void shouldDetectGzip() {
            assertEquals("GZIP", FileTypeUtil.detect(stream(GZIP_BYTES)));
        }

        @Test
        @DisplayName("应检测 MP3 文件")
        void shouldDetectMp3() {
            assertEquals("MP3", FileTypeUtil.detect(stream(MP3_BYTES)));
        }

        @Test
        @DisplayName("应检测 Java CLASS 文件")
        void shouldDetectJavaClass() {
            assertEquals("CLASS", FileTypeUtil.detect(stream(CLASS_BYTES)));
        }

        @Test
        @DisplayName("应检测 DOC (OLE2) 文件")
        void shouldDetectDocOle2() {
            assertEquals("DOC", FileTypeUtil.detect(stream(DOC_BYTES)));
        }

        @Test
        @DisplayName("应检测 DOCX 文件（ZIP 格式变体，头长度 > 6）")
        void shouldDetectDocx() {
            assertEquals("DOCX", FileTypeUtil.detect(stream(DOCX_BYTES)));
        }

        @Test
        @DisplayName("应检测 XML 文件")
        void shouldDetectXml() {
            assertEquals("XML", FileTypeUtil.detect(stream(XML_BYTES)));
        }

        @Test
        @DisplayName("未知字节序列应返回 UNKNOWN")
        void shouldReturnUnknownForUnrecognizedBytes() {
            assertEquals("UNKNOWN", FileTypeUtil.detect(stream(UNKNOWN_BYTES)));
        }

        @Test
        @DisplayName("空流应返回 UNKNOWN")
        void shouldReturnUnknownForEmptyStream() {
            assertEquals("UNKNOWN", FileTypeUtil.detect(new ByteArrayInputStream(new byte[0])));
        }

        @Test
        @DisplayName("流读取异常时应返回 UNKNOWN")
        void shouldReturnUnknownOnIOException() {
            InputStream badStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("模拟 IO 异常");
                }
            };
            assertEquals("UNKNOWN", FileTypeUtil.detect(badStream));
        }
    }

    // ======================== isImage 测试 ========================

    @Nested
    @DisplayName("isImage - 图片类型判断")
    class IsImageTests {

        @Test
        @DisplayName("PNG 应判定为图片")
        void shouldReturnTrueForPng() {
            assertTrue(FileTypeUtil.isImage(stream(PNG_BYTES)));
        }

        @Test
        @DisplayName("JPEG 应判定为图片")
        void shouldReturnTrueForJpeg() {
            assertTrue(FileTypeUtil.isImage(stream(JPEG_BYTES)));
        }

        @Test
        @DisplayName("GIF 应判定为图片")
        void shouldReturnTrueForGif() {
            assertTrue(FileTypeUtil.isImage(stream(GIF_BYTES)));
        }

        @Test
        @DisplayName("BMP 应判定为图片")
        void shouldReturnTrueForBmp() {
            assertTrue(FileTypeUtil.isImage(stream(BMP_BYTES)));
        }

        @Test
        @DisplayName("PDF 不应判定为图片")
        void shouldReturnFalseForPdf() {
            assertFalse(FileTypeUtil.isImage(stream(PDF_BYTES)));
        }

        @Test
        @DisplayName("ZIP 不应判定为图片")
        void shouldReturnFalseForZip() {
            assertFalse(FileTypeUtil.isImage(stream(ZIP_BYTES)));
        }
    }

    // ======================== isArchive 测试 ========================

    @Nested
    @DisplayName("isArchive - 压缩包类型判断")
    class IsArchiveTests {

        @Test
        @DisplayName("ZIP 应判定为压缩包")
        void shouldReturnTrueForZip() {
            assertTrue(FileTypeUtil.isArchive(stream(ZIP_BYTES)));
        }

        @Test
        @DisplayName("RAR 应判定为压缩包")
        void shouldReturnTrueForRar() {
            assertTrue(FileTypeUtil.isArchive(stream(RAR_BYTES)));
        }

        @Test
        @DisplayName("GZIP 应判定为压缩包")
        void shouldReturnTrueForGzip() {
            assertTrue(FileTypeUtil.isArchive(stream(GZIP_BYTES)));
        }

        @Test
        @DisplayName("PNG 不应判定为压缩包")
        void shouldReturnFalseForPng() {
            assertFalse(FileTypeUtil.isArchive(stream(PNG_BYTES)));
        }

        @Test
        @DisplayName("PDF 不应判定为压缩包")
        void shouldReturnFalseForPdf() {
            assertFalse(FileTypeUtil.isArchive(stream(PDF_BYTES)));
        }
    }

    // ======================== 辅助方法 ========================

    private static InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }
}
