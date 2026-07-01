package com.github.leyland.letool.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AI 核心模型测试")
class CoreModelsTest {

    @Nested
    @DisplayName("ChatMessage 消息模型")
    class ChatMessageTests {

        @Test
        @DisplayName("静态工厂 system 创建系统消息")
        void systemMessage() {
            ChatMessage msg = ChatMessage.system("你是助手");
            assertEquals("system", msg.getRole());
            assertEquals("你是助手", msg.getContent());
            assertTrue(msg.isSystem());
            assertFalse(msg.isUser());
            assertFalse(msg.isAssistant());
            assertFalse(msg.isFunction());
        }

        @Test
        @DisplayName("静态工厂 user 创建用户消息")
        void userMessage() {
            ChatMessage msg = ChatMessage.user("你好");
            assertEquals("user", msg.getRole());
            assertEquals("你好", msg.getContent());
            assertTrue(msg.isUser());
            assertFalse(msg.isSystem());
        }

        @Test
        @DisplayName("静态工厂 assistant 创建助手消息")
        void assistantMessage() {
            ChatMessage msg = ChatMessage.assistant("好的");
            assertEquals("assistant", msg.getRole());
            assertEquals("好的", msg.getContent());
            assertTrue(msg.isAssistant());
        }

        @Test
        @DisplayName("静态工厂 function 创建函数返回消息")
        void functionMessage() {
            ChatMessage msg = ChatMessage.function("get_weather", "晴天");
            assertEquals("function", msg.getRole());
            assertEquals("晴天", msg.getContent());
            assertEquals("get_weather", msg.getName());
            assertTrue(msg.isFunction());
        }

        @Test
        @DisplayName("构造方法创建消息")
        void constructorMessage() {
            ChatMessage msg = new ChatMessage("custom", "content");
            assertEquals("custom", msg.getRole());
            assertEquals("content", msg.getContent());
        }

        @Test
        @DisplayName("默认构造 + setter")
        void defaultConstructorAndSetters() {
            ChatMessage msg = new ChatMessage();
            msg.setRole("system");
            msg.setContent("test");
            msg.setName("testName");
            assertEquals("system", msg.getRole());
            assertEquals("test", msg.getContent());
            assertEquals("testName", msg.getName());
        }

        @Test
        @DisplayName("hasFunctionCall 检查函数调用")
        void hasFunctionCall() {
            ChatMessage msg = new ChatMessage();
            assertFalse(msg.hasFunctionCall());

            msg.setFunctionCall(new FunctionCall("test", "{}"));
            assertTrue(msg.hasFunctionCall());
        }

        @Test
        @DisplayName("FunctionCall getter / setter")
        void functionCallGetterSetter() {
            ChatMessage msg = new ChatMessage();
            FunctionCall fc = new FunctionCall();
            msg.setFunctionCall(fc);
            assertSame(fc, msg.getFunctionCall());
        }
    }

    @Nested
    @DisplayName("ChatRequest 对话请求构建器")
    class ChatRequestTests {

        @Test
        @DisplayName("Builder 链式构建完整请求")
        void builderComplete() {
            ChatRequest request = ChatRequest.builder()
                    .provider("openai")
                    .model("gpt-4o")
                    .addSystem("你是助手")
                    .addUser("你好")
                    .temperature(0.7)
                    .maxTokens(2048)
                    .build();

            assertEquals("openai", request.getProvider());
            assertEquals("gpt-4o", request.getModel());
            assertEquals(2, request.getMessages().size());
            assertEquals(0.7, request.getTemperature(), 0.001);
            assertEquals(2048, request.getMaxTokens());
        }

        @Test
        @DisplayName("Builder 默认值")
        void builderDefaults() {
            ChatRequest request = ChatRequest.builder().build();
            assertNull(request.getProvider());
            assertNull(request.getModel());
            assertEquals(0, request.getTemperature(), 0.001);
            assertEquals(0, request.getMaxTokens());
            assertTrue(request.getMessages().isEmpty());
            assertNull(request.getFunctions());
        }

        @Test
        @DisplayName("addMessage 添加消息对象")
        void addMessage() {
            ChatRequest request = ChatRequest.builder()
                    .addMessage(ChatMessage.system("system msg"))
                    .addMessage(ChatMessage.user("user msg"))
                    .addMessage(ChatMessage.assistant("assistant msg"))
                    .build();

            assertEquals(3, request.getMessages().size());
            assertTrue(request.getMessages().get(0).isSystem());
            assertTrue(request.getMessages().get(1).isUser());
            assertTrue(request.getMessages().get(2).isAssistant());
        }

        @Test
        @DisplayName("addSystem / addUser / addAssistant 便捷方法")
        void addConvenienceMethods() {
            ChatRequest request = ChatRequest.builder()
                    .addSystem("S")
                    .addUser("U")
                    .addAssistant("A")
                    .build();

            assertEquals(3, request.getMessages().size());
            assertTrue(request.getMessages().get(0).isSystem());
            assertTrue(request.getMessages().get(1).isUser());
            assertTrue(request.getMessages().get(2).isAssistant());
        }

        @Test
        @DisplayName("messages() 覆盖消息列表")
        void messagesOverride() {
            ChatRequest request = ChatRequest.builder()
                    .addSystem("first")
                    .messages(List.of(ChatMessage.user("replaced")))
                    .build();

            assertEquals(1, request.getMessages().size());
            assertTrue(request.getMessages().get(0).isUser());
        }

        @Test
        @DisplayName("functions() 设置函数定义")
        void functionsSetting() {
            FunctionDefinition fd = FunctionDefinition.builder()
                    .name("test").description("test func").build();

            ChatRequest request = ChatRequest.builder()
                    .functions(List.of(fd))
                    .build();

            assertEquals(1, request.getFunctions().size());
            assertEquals("test", request.getFunctions().get(0).getName());
        }

        @Test
        @DisplayName("setter 方法")
        void setters() {
            ChatRequest request = new ChatRequest();
            request.setProvider("deepseek");
            request.setModel("deepseek-chat");
            request.setTemperature(0.5);
            request.setMaxTokens(1000);
            request.setMessages(List.of(ChatMessage.user("hi")));
            request.setFunctions(List.of());

            assertEquals("deepseek", request.getProvider());
            assertEquals("deepseek-chat", request.getModel());
            assertEquals(0.5, request.getTemperature(), 0.001);
            assertEquals(1000, request.getMaxTokens());
            assertEquals(1, request.getMessages().size());
            assertTrue(request.getFunctions().isEmpty());
        }
    }

    @Nested
    @DisplayName("ChatResponse 对话响应模型")
    class ChatResponseTests {

        @Test
        @DisplayName("getContent 返回 choice 内容")
        void getContentWithChoice() {
            ChatResponse response = new ChatResponse();
            ChatResponse.ChatChoice choice = new ChatResponse.ChatChoice();
            choice.setContent("你好！");
            response.setChoice(choice);

            assertEquals("你好！", response.getContent());
        }

        @Test
        @DisplayName("getContent choice 为 null 返回 null")
        void getContentNullChoice() {
            ChatResponse response = new ChatResponse();
            assertNull(response.getContent());
        }

        @Test
        @DisplayName("hasFunctionCall 检查函数调用")
        void hasFunctionCall() {
            ChatResponse response = new ChatResponse();

            assertFalse(response.hasFunctionCall());

            ChatResponse.ChatChoice choice = new ChatResponse.ChatChoice();
            response.setChoice(choice);
            assertFalse(response.hasFunctionCall());

            choice.setFunctionCall(new FunctionCall("get_weather", "{}"));
            assertTrue(response.hasFunctionCall());
        }

        @Test
        @DisplayName("ChatChoice getter / setter")
        void chatChoiceFields() {
            ChatResponse.ChatChoice choice = new ChatResponse.ChatChoice();
            choice.setContent("text");
            choice.setRole("assistant");
            choice.setFinishReason("stop");

            assertEquals("text", choice.getContent());
            assertEquals("assistant", choice.getRole());
            assertEquals("stop", choice.getFinishReason());
            assertNull(choice.getFunctionCall());
        }

        @Test
        @DisplayName("Usage Token 统计")
        void usageFields() {
            ChatResponse.Usage usage = new ChatResponse.Usage();
            usage.setPromptTokens(100);
            usage.setCompletionTokens(50);
            usage.setTotalTokens(150);

            assertEquals(100, usage.getPromptTokens());
            assertEquals(50, usage.getCompletionTokens());
            assertEquals(150, usage.getTotalTokens());
        }

        @Test
        @DisplayName("Response 完整字段 getter / setter")
        void responseFields() {
            ChatResponse response = new ChatResponse();
            response.setId("resp-001");
            response.setProvider("openai");
            response.setModel("gpt-4o");
            response.setLatencyMs(1500);

            ChatResponse.ChatChoice choice = new ChatResponse.ChatChoice();
            choice.setContent("reply");
            response.setChoice(choice);

            ChatResponse.Usage usage = new ChatResponse.Usage();
            usage.setTotalTokens(200);
            response.setUsage(usage);

            assertEquals("resp-001", response.getId());
            assertEquals("openai", response.getProvider());
            assertEquals("gpt-4o", response.getModel());
            assertEquals("reply", response.getContent());
            assertEquals(200, response.getUsage().getTotalTokens());
            assertEquals(1500, response.getLatencyMs());
        }
    }

    @Nested
    @DisplayName("FunctionCall 函数调用模型")
    class FunctionCallTests {

        @Test
        @DisplayName("构造方法创建")
        void constructor() {
            FunctionCall fc = new FunctionCall("get_weather", "{\"city\":\"北京\"}");
            assertEquals("get_weather", fc.getName());
            assertEquals("{\"city\":\"北京\"}", fc.getArguments());
        }

        @Test
        @DisplayName("默认构造 + setter")
        void defaultConstructorAndSetters() {
            FunctionCall fc = new FunctionCall();
            fc.setName("search");
            fc.setArguments("{\"q\":\"test\"}");
            assertEquals("search", fc.getName());
            assertEquals("{\"q\":\"test\"}", fc.getArguments());
        }
    }
}
