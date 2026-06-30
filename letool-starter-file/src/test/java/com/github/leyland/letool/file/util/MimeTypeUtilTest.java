package com.github.leyland.letool.file.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MimeTypeUtil 单元测试。
 *
 * @author leyland
 */
@DisplayName("MimeTypeUtil MIME类型查询测试")
class MimeTypeUtilTest {

    // ======================== getMimeType 测试 ========================

    @Nested
    @DisplayName("getMimeType - 根据文件名获取 MIME 类型")
    class GetMimeTypeTests {

        @ParameterizedTest(name = "文件名 \"{0}\" 应返回 MIME 类型 \"{1}\"")
        @CsvSource({
                "photo.jpg, image/jpeg",
                "photo.jpeg, image/jpeg",
                "screenshot.png, image/png",
                "animation.gif, image/gif",
                "image.bmp, image/bmp",
                "icon.svg, image/svg+xml",
                "picture.webp, image/webp",
        })
        @DisplayName("图片类型应返回正确的 MIME")
        void shouldReturnImageMime(String fileName, String expectedMime) {
            assertEquals(expectedMime, MimeTypeUtil.getMimeType(fileName));
        }

        @ParameterizedTest(name = "文件名 \"{0}\" 应返回 MIME 类型 \"{1}\"")
        @CsvSource({
                "report.pdf, application/pdf",
                "document.doc, application/msword",
                "document.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "sheet.xls, application/vnd.ms-excel",
                "sheet.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "slides.ppt, application/vnd.ms-powerpoint",
                "slides.pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation",
        })
        @DisplayName("文档类型应返回正确的 MIME")
        void shouldReturnDocumentMime(String fileName, String expectedMime) {
            assertEquals(expectedMime, MimeTypeUtil.getMimeType(fileName));
        }

        @ParameterizedTest(name = "文件名 \"{0}\" 应返回 MIME 类型 \"{1}\"")
        @CsvSource({
                "readme.txt, text/plain",
                "index.html, text/html",
                "style.css, text/css",
                "app.js, application/javascript",
                "data.json, application/json",
                "config.xml, application/xml",
        })
        @DisplayName("文本类型应返回正确的 MIME")
        void shouldReturnTextMime(String fileName, String expectedMime) {
            assertEquals(expectedMime, MimeTypeUtil.getMimeType(fileName));
        }

        @ParameterizedTest(name = "文件名 \"{0}\" 应返回 MIME 类型 \"{1}\"")
        @CsvSource({
                "archive.zip, application/zip",
                "archive.rar, application/x-rar-compressed",
                "archive.gz, application/gzip",
                "archive.tar, application/x-tar",
        })
        @DisplayName("压缩类型应返回正确的 MIME")
        void shouldReturnArchiveMime(String fileName, String expectedMime) {
            assertEquals(expectedMime, MimeTypeUtil.getMimeType(fileName));
        }

        @ParameterizedTest(name = "文件名 \"{0}\" 应返回 MIME 类型 \"{1}\"")
        @CsvSource({
                "song.mp3, audio/mpeg",
                "video.mp4, video/mp4",
                "movie.avi, video/x-msvideo",
                "clip.mov, video/quicktime",
        })
        @DisplayName("音视频类型应返回正确的 MIME")
        void shouldReturnMediaMime(String fileName, String expectedMime) {
            assertEquals(expectedMime, MimeTypeUtil.getMimeType(fileName));
        }

        @Test
        @DisplayName("扩展名大小写不敏感")
        void shouldBeCaseInsensitive() {
            assertEquals("image/png", MimeTypeUtil.getMimeType("PHOTO.PNG"));
            assertEquals("application/pdf", MimeTypeUtil.getMimeType("REPORT.Pdf"));
            assertEquals("text/plain", MimeTypeUtil.getMimeType("readme.TXT"));
        }

        @Test
        @DisplayName("未知扩展名应返回 application/octet-stream")
        void shouldReturnOctetStreamForUnknownExtension() {
            assertEquals("application/octet-stream", MimeTypeUtil.getMimeType("data.xyz"));
            assertEquals("application/octet-stream", MimeTypeUtil.getMimeType("file.unknown"));
            assertEquals("application/octet-stream", MimeTypeUtil.getMimeType("binary.abc123"));
        }

        @Test
        @DisplayName("无扩展名文件应返回 application/octet-stream")
        void shouldReturnOctetStreamForNoExtension() {
            assertEquals("application/octet-stream", MimeTypeUtil.getMimeType("Makefile"));
            assertEquals("application/octet-stream", MimeTypeUtil.getMimeType("README"));
        }

        @Test
        @DisplayName("null 输入应返回 application/octet-stream")
        void shouldReturnOctetStreamForNull() {
            assertEquals("application/octet-stream", MimeTypeUtil.getMimeType(null));
        }
    }

    // ======================== getMimeTypeByExt 测试 ========================

    @Nested
    @DisplayName("getMimeTypeByExt - 根据扩展名字符串获取 MIME 类型")
    class GetMimeTypeByExtTests {

        @Test
        @DisplayName("不带点号的扩展名应正确返回 MIME 类型")
        void shouldReturnMimeForExtensionWithoutDot() {
            assertEquals("image/png", MimeTypeUtil.getMimeTypeByExt("png"));
            assertEquals("application/pdf", MimeTypeUtil.getMimeTypeByExt("pdf"));
            assertEquals("application/zip", MimeTypeUtil.getMimeTypeByExt("zip"));
        }

        @Test
        @DisplayName("扩展名大小写不敏感")
        void shouldBeCaseInsensitive() {
            assertEquals("image/png", MimeTypeUtil.getMimeTypeByExt("PNG"));
            assertEquals("application/pdf", MimeTypeUtil.getMimeTypeByExt("Pdf"));
        }

        @Test
        @DisplayName("未知扩展名应返回 application/octet-stream")
        void shouldReturnOctetStreamForUnknownExtension() {
            assertEquals("application/octet-stream", MimeTypeUtil.getMimeTypeByExt("xyz"));
        }

        @Test
        @DisplayName("null 扩展名应返回 application/octet-stream")
        void shouldReturnOctetStreamForNull() {
            assertEquals("application/octet-stream", MimeTypeUtil.getMimeTypeByExt(null));
        }
    }
}
