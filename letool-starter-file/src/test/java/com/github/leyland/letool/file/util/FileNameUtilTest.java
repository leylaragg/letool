package com.github.leyland.letool.file.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileNameUtil 单元测试。
 *
 * @author leyland
 */
@DisplayName("FileNameUtil 工具类测试")
class FileNameUtilTest {

    // ======================== getExtension 测试 ========================

    @Nested
    @DisplayName("getExtension - 扩展名提取")
    class GetExtensionTests {

        @Test
        @DisplayName("正常文件名应返回小写扩展名")
        void shouldReturnLowercaseExtension() {
            assertEquals("jpg", FileNameUtil.getExtension("photo.JPG"));
            assertEquals("pdf", FileNameUtil.getExtension("report.PDF"));
            assertEquals("png", FileNameUtil.getExtension("screenshot.PnG"));
        }

        @Test
        @DisplayName("无扩展名的文件名应返回空字符串")
        void shouldReturnEmptyWhenNoExtension() {
            assertEquals("", FileNameUtil.getExtension("Makefile"));
            assertEquals("", FileNameUtil.getExtension("README"));
            assertEquals("", FileNameUtil.getExtension("noext"));
        }

        @Test
        @DisplayName("null 输入应返回空字符串")
        void shouldReturnEmptyWhenNull() {
            assertEquals("", FileNameUtil.getExtension(null));
        }

        @Test
        @DisplayName("包含路径的文件名应正确提取扩展名")
        void shouldExtractExtensionFromPath() {
            assertEquals("txt", FileNameUtil.getExtension("/home/user/docs/note.txt"));
            assertEquals("java", FileNameUtil.getExtension("src\\main\\java\\App.java"));
        }

        @Test
        @DisplayName("多个点号的文件名应返回最后一个扩展名")
        void shouldReturnLastExtensionForMultiDot() {
            assertEquals("gz", FileNameUtil.getExtension("archive.tar.gz"));
            // 2024 是最后一个点号之后的子串（最后一级扩展名）
            assertEquals("2024", FileNameUtil.getExtension("config.backup.2024"));
        }

        @Test
        @DisplayName("以点号开头的文件名应提取点号之后的内容作为扩展名")
        void shouldExtractAfterDotForLeadingDotFiles() {
            // .gitignore 中的 ".gitignore" -> lastIndexOf('.') = 0 -> substring(1) = "gitignore"
            assertEquals("gitignore", FileNameUtil.getExtension(".gitignore"));
            assertEquals("hidden", FileNameUtil.getExtension(".hidden"));
        }
    }

    // ======================== generateUniqueName 测试 ========================

    @Nested
    @DisplayName("generateUniqueName - 唯一文件名生成")
    class GenerateUniqueNameTests {

        @Test
        @DisplayName("应生成包含 UUID 和原扩展名的唯一文件名")
        void shouldGenerateUniqueNameWithExtension() {
            String name = FileNameUtil.generateUniqueName("report.pdf");
            assertNotNull(name);
            assertTrue(name.endsWith(".pdf"), "生成的文件名应以 .pdf 结尾");
            assertFalse(name.contains("-"), "UUID 中的横线应被移除");

            // UUID (32 位十六进制) + "." + 扩展名
            String base = name.substring(0, name.lastIndexOf('.'));
            assertEquals(32, base.length(), "基础名应为 32 位十六进制字符");
            assertTrue(base.matches("[0-9a-f]{32}"), "基础名应全部为小写十六进制字符");
        }

        @Test
        @DisplayName("无扩展名的原始文件名应生成无扩展名的唯一名")
        void shouldGenerateUniqueNameWithoutExtension() {
            String name = FileNameUtil.generateUniqueName("Makefile");
            assertNotNull(name);
            assertFalse(name.contains("."), "无扩展名原始文件不应生成带点号的名称");
            assertEquals(32, name.length());
            assertTrue(name.matches("[0-9a-f]{32}"));
        }

        @Test
        @DisplayName("null 输入应生成无扩展名的唯一名")
        void shouldGenerateUniqueNameWhenNull() {
            String name = FileNameUtil.generateUniqueName(null);
            assertNotNull(name);
            assertFalse(name.contains("."));
            assertEquals(32, name.length());
        }

        @Test
        @DisplayName("每次调用应生成不同的文件名")
        void shouldGenerateDifferentNamesOnEachCall() {
            String name1 = FileNameUtil.generateUniqueName("test.txt");
            String name2 = FileNameUtil.generateUniqueName("test.txt");
            assertNotEquals(name1, name2);
        }
    }

    // ======================== removeExtension 测试 ========================

    @Nested
    @DisplayName("removeExtension - 移除扩展名")
    class RemoveExtensionTests {

        @Test
        @DisplayName("正常文件名应移除扩展名部分")
        void shouldRemoveExtension() {
            assertEquals("photo", FileNameUtil.removeExtension("photo.jpg"));
            assertEquals("report", FileNameUtil.removeExtension("report.pdf"));
        }

        @Test
        @DisplayName("多扩展名文件应只移除最后一级扩展名")
        void shouldRemoveOnlyLastExtension() {
            assertEquals("archive.tar", FileNameUtil.removeExtension("archive.tar.gz"));
            assertEquals("config.backup", FileNameUtil.removeExtension("config.backup.2024"));
        }

        @Test
        @DisplayName("无扩展名的文件应原样返回")
        void shouldReturnOriginalWhenNoExtension() {
            assertEquals("Makefile", FileNameUtil.removeExtension("Makefile"));
            assertEquals("README", FileNameUtil.removeExtension("README"));
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullWhenInputIsNull() {
            assertNull(FileNameUtil.removeExtension(null));
        }
    }

    // ======================== sanitize 测试 ========================

    @Nested
    @DisplayName("sanitize - 文件名安全清洗")
    class SanitizeTests {

        @Test
        @DisplayName("应将非法字符替换为下划线")
        void shouldReplaceIllegalCharsWithUnderscore() {
            assertEquals("test_file.txt", FileNameUtil.sanitize("test:file.txt"));
            assertEquals("test_file.txt", FileNameUtil.sanitize("test*file.txt"));
            assertEquals("test_file.txt", FileNameUtil.sanitize("test?file.txt"));
            assertEquals("test_file.txt", FileNameUtil.sanitize("test\"file.txt"));
            assertEquals("test_file.txt", FileNameUtil.sanitize("test<file.txt"));
            assertEquals("test_file.txt", FileNameUtil.sanitize("test>file.txt"));
            assertEquals("test_file.txt", FileNameUtil.sanitize("test|file.txt"));
            assertEquals("test_file.txt", FileNameUtil.sanitize("test/file.txt"));
            assertEquals("test_file.txt", FileNameUtil.sanitize("test\\file.txt"));
        }

        @Test
        @DisplayName("应去除头尾空格")
        void shouldTrimWhitespace() {
            assertEquals("file.txt", FileNameUtil.sanitize("  file.txt  "));
            assertEquals("file.txt", FileNameUtil.sanitize("\tfile.txt\n"));
        }

        @Test
        @DisplayName("同时包含非法字符和空格应正确处理")
        void shouldHandleIllegalCharsAndWhitespaceTogether() {
            assertEquals("clean_name.txt", FileNameUtil.sanitize("  clean:name.txt  "));
        }

        @Test
        @DisplayName("正常文件名应保持不变")
        void shouldKeepValidFileNameUnchanged() {
            assertEquals("my-photo_2024.jpg", FileNameUtil.sanitize("my-photo_2024.jpg"));
            assertEquals("report (final).pdf", FileNameUtil.sanitize("report (final).pdf"));
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullWhenInputIsNull() {
            assertNull(FileNameUtil.sanitize(null));
        }
    }
}
