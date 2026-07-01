package com.github.leyland.letool.ai.chat;

import com.github.leyland.letool.ai.core.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptTemplate 提示词模板测试")
class PromptTemplateTest {

    private final PromptTemplate template = new PromptTemplate();

    @Nested
    @DisplayName("render 渲染模板")
    class RenderTests {

        @Test
        @DisplayName("替换单个变量")
        void renderSingleVariable() {
            String result = template.render("你好，{{name}}！", Map.of("name", "小明"));
            assertEquals("你好，小明！", result);
        }

        @Test
        @DisplayName("替换多个变量")
        void renderMultipleVariables() {
            String result = template.render(
                    "{{greeting}}，{{name}}！今天是{{day}}。",
                    Map.of("greeting", "你好", "name", "小明", "day", "星期一"));
            assertEquals("你好，小明！今天是星期一。", result);
        }

        @Test
        @DisplayName("替换非字符串变量（调用 toString）")
        void renderNonStringValue() {
            String result = template.render("数量：{{count}}", Map.of("count", 42));
            assertEquals("数量：42", result);
        }

        @Test
        @DisplayName("替换包含空格的占位符")
        void renderWithSpacesInPlaceholder() {
            String result = template.render("你好，{{ name }}！", Map.of("name", "小明"));
            assertEquals("你好，小明！", result);
        }

        @Test
        @DisplayName("null 模板返回 null")
        void renderNullTemplate() {
            assertNull(template.render(null, Map.of("key", "value")));
        }

        @Test
        @DisplayName("空字符串模板返回空字符串")
        void renderEmptyTemplate() {
            assertEquals("", template.render("", Map.of("key", "value")));
        }

        @Test
        @DisplayName("null 变量 Map 返回原模板")
        void renderNullVariables() {
            assertEquals("你好，{{name}}", template.render("你好，{{name}}", null));
        }

        @Test
        @DisplayName("空变量 Map 返回原模板")
        void renderEmptyVariables() {
            assertEquals("你好，{{name}}", template.render("你好，{{name}}", Collections.emptyMap()));
        }

        @Test
        @DisplayName("变量不存在保持占位符原样")
        void renderMissingVariableKeepsPlaceholder() {
            String result = template.render("你好，{{name}}", Map.of("other", "value"));
            assertEquals("你好，{{name}}", result);
        }

        @Test
        @DisplayName("无占位符模板返回原样")
        void renderNoPlaceholders() {
            assertEquals("Hello World", template.render("Hello World", Map.of("key", "value")));
        }

        @Test
        @DisplayName("模板中同一变量出现多次")
        void renderRepeatedVariable() {
            String result = template.render("{{name}}你好，{{name}}再见", Map.of("name", "小明"));
            assertEquals("小明你好，小明再见", result);
        }
    }

    @Nested
    @DisplayName("renderSystem / renderUser / renderAssistant")
    class RenderMessageTests {

        @Test
        @DisplayName("renderSystem 返回系统角色消息")
        void renderSystem() {
            ChatMessage msg = template.renderSystem("你是{{role}}", Map.of("role", "翻译官"));
            assertTrue(msg.isSystem());
            assertEquals("你是翻译官", msg.getContent());
        }

        @Test
        @DisplayName("renderUser 返回用户角色消息")
        void renderUser() {
            ChatMessage msg = template.renderUser("翻译：{{text}}", Map.of("text", "Hello"));
            assertTrue(msg.isUser());
            assertEquals("翻译：Hello", msg.getContent());
        }

        @Test
        @DisplayName("renderAssistant 返回助手角色消息")
        void renderAssistant() {
            ChatMessage msg = template.renderAssistant("结果是{{result}}", Map.of("result", "成功"));
            assertTrue(msg.isAssistant());
            assertEquals("结果是成功", msg.getContent());
        }
    }

    @Nested
    @DisplayName("hasPlaceholders 检查占位符")
    class HasPlaceholdersTests {

        @Test
        @DisplayName("包含占位符返回 true")
        void hasPlaceholdersTrue() {
            assertTrue(template.hasPlaceholders("你好，{{name}}"));
        }

        @Test
        @DisplayName("多个占位符返回 true")
        void hasPlaceholdersMultiple() {
            assertTrue(template.hasPlaceholders("{{a}} {{b}} {{c}}"));
        }

        @Test
        @DisplayName("不包含占位符返回 false")
        void hasPlaceholdersFalse() {
            assertFalse(template.hasPlaceholders("Hello World"));
        }

        @Test
        @DisplayName("null 返回 false")
        void hasPlaceholdersNull() {
            assertFalse(template.hasPlaceholders(null));
        }

        @Test
        @DisplayName("空字符串返回 false")
        void hasPlaceholdersEmpty() {
            assertFalse(template.hasPlaceholders(""));
        }
    }
}
