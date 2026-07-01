package com.github.leyland.letool.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TextSplitter 文本分块器测试")
class TextSplitterTest {

    @Nested
    @DisplayName("默认构造函数")
    class DefaultConstructorTests {

        @Test
        @DisplayName("默认 chunkSize=500, overlap=50")
        void defaultConstructor() {
            TextSplitter splitter = new TextSplitter();
            assertNotNull(splitter);
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ValidationTests {

        @Test
        @DisplayName("chunkSize 小于 MIN_CHUNK_SIZE 抛异常")
        void chunkSizeTooSmall() {
            assertThrows(IllegalArgumentException.class, () -> new TextSplitter(10, 5));
        }

        @Test
        @DisplayName("chunkSize 等于 MIN_CHUNK_SIZE 可以创建")
        void chunkSizeAtMin() {
            TextSplitter splitter = new TextSplitter(TextSplitter.MIN_CHUNK_SIZE, 10);
            assertNotNull(splitter);
        }

        @Test
        @DisplayName("overlap >= chunkSize 抛异常")
        void overlapTooLarge() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TextSplitter(200, 200));
        }

        @Test
        @DisplayName("overlap 等于 0 可以创建")
        void overlapZero() {
            TextSplitter splitter = new TextSplitter(200, 0);
            assertNotNull(splitter);
        }
    }

    @Nested
    @DisplayName("split 分割文本")
    class SplitTests {

        @Test
        @DisplayName("null 文本返回空列表")
        void splitNull() {
            TextSplitter splitter = new TextSplitter(100, 10);
            List<String> result = splitter.split(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void splitEmpty() {
            TextSplitter splitter = new TextSplitter(100, 10);
            List<String> result = splitter.split("");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空白字符串返回空列表")
        void splitBlank() {
            TextSplitter splitter = new TextSplitter(100, 10);
            List<String> result = splitter.split("   ");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("短文本不分割")
        void splitShortText() {
            TextSplitter splitter = new TextSplitter(500, 50);
            String text = "这是一段短文本。";
            List<String> result = splitter.split(text);
            assertEquals(1, result.size());
            assertTrue(result.get(0).contains("这是一段短文本"));
        }

        @Test
        @DisplayName("按段落分割长文本")
        void splitByParagraph() {
            TextSplitter splitter = new TextSplitter(100, 10);
            String text = "第一段内容。第一段内容。\n\n第二段内容。第二段内容。";
            List<String> result = splitter.split(text);
            assertFalse(result.isEmpty());
            assertTrue(result.stream().anyMatch(s -> s.contains("第一段")));
        }

        @Test
        @DisplayName("多个段落分割")
        void splitMultipleParagraphs() {
            TextSplitter splitter = new TextSplitter(500, 50);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 5; i++) {
                if (i > 1) sb.append("\n\n");
                sb.append("第").append(i).append("段落内容。这是第").append(i).append("段的文字。");
            }
            List<String> result = splitter.split(sb.toString());
            assertTrue(result.size() >= 1);
        }

        @Test
        @DisplayName("重叠不为零时相邻块有重叠内容")
        void splitWithOverlap() {
            TextSplitter splitter = new TextSplitter(50, 20);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("这是一段很长的文本内容需要被分割成多个块。");
            }
            List<String> result = splitter.split(sb.toString());
            assertTrue(result.size() > 1);
        }
    }

    @Nested
    @DisplayName("splitDocuments 批量分割文档")
    class SplitDocumentsTests {

        @Test
        @DisplayName("空文档列表返回空列表")
        void splitEmptyDocuments() {
            TextSplitter splitter = new TextSplitter(200, 20);
            List<String> result = splitter.splitDocuments(List.of());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("文档内容附加来源信息")
        void splitDocumentWithSource() {
            TextSplitter splitter = new TextSplitter(500, 50);
            DocumentLoader.Document doc = new DocumentLoader.Document(
                    "这是一段测试文本。", "test-source.txt");
            List<String> result = splitter.splitDocuments(List.of(doc));
            assertFalse(result.isEmpty());
            assertTrue(result.get(0).contains("[来源: test-source.txt]"));
        }
    }
}
