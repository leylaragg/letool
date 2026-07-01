package com.github.leyland.letool.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmbeddingRequest 嵌入请求测试")
class EmbeddingRequestTest {

    @Nested
    @DisplayName("Builder 链式构建")
    class BuilderTests {

        @Test
        @DisplayName("完整构建")
        void builderComplete() {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .provider("openai")
                    .model("text-embedding-3-small")
                    .input("你好世界")
                    .input("Hello World")
                    .build();

            assertEquals("openai", request.getProvider());
            assertEquals("text-embedding-3-small", request.getModel());
            assertEquals(2, request.getInput().size());
            assertTrue(request.getInput().contains("你好世界"));
            assertTrue(request.getInput().contains("Hello World"));
        }

        @Test
        @DisplayName("默认值")
        void builderDefaults() {
            EmbeddingRequest request = EmbeddingRequest.builder().build();
            assertNull(request.getProvider());
            assertNull(request.getModel());
            assertTrue(request.getInput().isEmpty());
        }

        @Test
        @DisplayName("input(String) 逐条添加")
        void inputSingleString() {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input("text1")
                    .input("text2")
                    .input("text3")
                    .build();

            assertEquals(3, request.getInput().size());
            assertEquals("text1", request.getInput().get(0));
            assertEquals("text2", request.getInput().get(1));
            assertEquals("text3", request.getInput().get(2));
        }

        @Test
        @DisplayName("input(String...) 批量添加")
        void inputVarargs() {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input("a", "b", "c", "d")
                    .build();

            assertEquals(4, request.getInput().size());
            assertTrue(request.getInput().containsAll(List.of("a", "b", "c", "d")));
        }

        @Test
        @DisplayName("input(List) 覆盖列表")
        void inputListOverrides() {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input("old1")
                    .input(List.of("new1", "new2"))
                    .build();

            assertEquals(2, request.getInput().size());
            assertEquals("new1", request.getInput().get(0));
            assertEquals("new2", request.getInput().get(1));
        }

        @Test
        @DisplayName("混合 input 方法")
        void inputMixed() {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input("single")
                    .input("a", "b")
                    .input("another")
                    .build();

            assertEquals(4, request.getInput().size());
        }
    }

    @Nested
    @DisplayName("getter / setter")
    class GetterSetterTests {

        @Test
        @DisplayName("setProvider / setModel")
        void providerAndModel() {
            EmbeddingRequest request = new EmbeddingRequest();
            request.setProvider("deepseek");
            request.setModel("text-embedding-ada-002");

            assertEquals("deepseek", request.getProvider());
            assertEquals("text-embedding-ada-002", request.getModel());
        }

        @Test
        @DisplayName("setInput 设置列表")
        void setInput() {
            EmbeddingRequest request = new EmbeddingRequest();
            request.setInput(List.of("a", "b", "c"));

            assertEquals(3, request.getInput().size());
            assertEquals(List.of("a", "b", "c"), request.getInput());
        }

        @Test
        @DisplayName("默认构造 input 为空列表")
        void defaultConstructorEmptyInput() {
            EmbeddingRequest request = new EmbeddingRequest();
            assertNotNull(request.getInput());
            assertTrue(request.getInput().isEmpty());
        }
    }
}
