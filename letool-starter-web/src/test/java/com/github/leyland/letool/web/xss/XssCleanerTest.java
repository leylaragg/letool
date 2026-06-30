package com.github.leyland.letool.web.xss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XSS 清理器测试")
class XssCleanerTest {

    @Test
    @DisplayName("null 输入返回 null")
    void nullInput() {
        assertNull(XssCleaner.clean(null));
    }

    @Test
    @DisplayName("普通文本不做转义")
    void plainText() {
        assertEquals("hello world", XssCleaner.clean("hello world"));
    }

    @Test
    @DisplayName("script 标签被转义")
    void scriptTagEscaped() {
        String input = "<script>alert('xss')</script>";
        String result = XssCleaner.clean(input);

        assertFalse(result.contains("<script>"));
        assertTrue(result.contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("HTML 标签被转义")
    void htmlTagsEscaped() {
        String result = XssCleaner.clean("<b>bold</b>");

        assertTrue(result.contains("&lt;b&gt;"));
    }

    @Test
    @DisplayName("特殊字符转义")
    void specialCharsEscaped() {
        String result = XssCleaner.clean("\"quoted\" & 'apos'");

        assertTrue(result.contains("&quot;"));
        assertTrue(result.contains("&amp;"));
    }

    @Test
    @DisplayName("合法中文不受影响")
    void chineseText() {
        assertEquals("这是正常的中文文本", XssCleaner.clean("这是正常的中文文本"));
    }
}
