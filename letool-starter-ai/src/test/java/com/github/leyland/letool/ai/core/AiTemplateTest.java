package com.github.leyland.letool.ai.core;

import com.github.leyland.letool.ai.exception.AiErrorCode;
import com.github.leyland.letool.ai.exception.AiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AiTemplate} 原生 Spring AI 门面行为测试。
 */
@DisplayName("AiTemplate 原生 Spring AI 门面测试")
class AiTemplateTest {

    /**
     * 验证默认客户端能够直接调用注册的 Spring AI 对话模型并返回原生响应。
     */
    @Test
    @DisplayName("默认客户端调用原生 ChatModel")
    void shouldCallDefaultChatModelThroughNativeClient() {
        TestChatModel model = new TestChatModel("默认回复");
        AiTemplate template = template(Map.of("primary", model), Map.of(), null, List.of());

        ChatResponse response = template.chatClient()
                .prompt()
                .user("你好")
                .call()
                .chatResponse();

        assertEquals("默认回复", response.getResult().getOutput().getText());
        assertEquals("你好", model.lastPrompt.getContents());
    }

    /**
     * 验证可以按 Spring Bean 名称选择对话客户端。
     */
    @Test
    @DisplayName("按名称选择对话客户端")
    void shouldSelectChatClientByBeanName() {
        AiTemplate template = template(
                Map.of(
                        "primary", new TestChatModel("主模型"),
                        "backup", new TestChatModel("备用模型")),
                Map.of(),
                "primary",
                List.of());

        String content = template.chatClient("backup").prompt("请求").call().content();

        assertEquals("备用模型", content);
    }

    /**
     * 验证同名客户端只构建一次并从不可变缓存重复返回。
     */
    @Test
    @DisplayName("同名客户端重复查询返回同一实例")
    void shouldCacheChatClientByBeanName() {
        AiTemplate template = template(
                Map.of("primary", new TestChatModel("回复")),
                Map.of(),
                null,
                List.of());

        assertSame(template.chatClient(), template.chatClient("primary"));
        assertSame(template.chatClient("primary"), template.chatClient("primary"));
    }

    /**
     * 验证模型查询与名称查询完整委托给不可变注册表。
     */
    @Test
    @DisplayName("委托模型与名称查询")
    void shouldDelegateModelsAndNamesToRegistry() {
        ChatModel chatModel = new TestChatModel("回复");
        EmbeddingModel embeddingModel = new TestEmbeddingModel();
        AiTemplate template = template(
                Map.of("chat", chatModel),
                Map.of("embedding", embeddingModel),
                null,
                List.of());

        assertSame(chatModel, template.chatModel());
        assertSame(chatModel, template.chatModel("chat"));
        assertSame(embeddingModel, template.embeddingModel());
        assertSame(embeddingModel, template.embeddingModel("embedding"));
        assertEquals(List.of("chat"), List.copyOf(template.chatModelNames()));
        assertEquals(List.of("embedding"), List.copyOf(template.embeddingModelNames()));
    }

    /**
     * 验证客户端定制器遵循 Spring 排序规则执行。
     */
    @Test
    @DisplayName("定制器按 Spring 顺序执行")
    void shouldApplyCustomizersInSpringOrder() {
        List<String> invocationOrder = new ArrayList<>();
        AiChatClientCustomizer late = new OrderedCustomizer(20, "late", invocationOrder);
        AiChatClientCustomizer early = new OrderedCustomizer(-20, "early", invocationOrder);

        template(
                Map.of("primary", new TestChatModel("回复")),
                Map.of(),
                null,
                List.of(late, early));

        assertEquals(List.of("early:primary", "late:primary"), invocationOrder);
    }

    /**
     * 验证每个模型都获得独立构建器，并把准确的 Bean 名称传给定制器。
     */
    @Test
    @DisplayName("每个模型使用独立构建器并传递模型名称")
    void shouldProvideModelNameAndIndependentBuilderToCustomizer() {
        Map<String, ChatClient.Builder> builders = new LinkedHashMap<>();
        AiChatClientCustomizer customizer = builders::put;

        template(
                Map.of(
                        "alpha", new TestChatModel("甲"),
                        "beta", new TestChatModel("乙")),
                Map.of(),
                "alpha",
                List.of(customizer));

        assertEquals(List.of("alpha", "beta"), List.copyOf(builders.keySet()));
        assertNotSame(builders.get("alpha"), builders.get("beta"));
    }

    /**
     * 验证定制器失败会转换为结构化异常并完整保留原因链。
     */
    @Test
    @DisplayName("定制失败转换为结构化异常")
    void shouldWrapCustomizerFailureWithCause() {
        IllegalStateException cause = new IllegalStateException("定制器故障");
        AiChatClientCustomizer customizer = (modelName, builder) -> {
            throw cause;
        };

        AiException exception = assertThrows(
                AiException.class,
                () -> template(
                        Map.of("primary", new TestChatModel("回复")),
                        Map.of(),
                        null,
                        List.of(customizer)));

        assertSame(AiErrorCode.CLIENT_CUSTOMIZATION_FAILED, exception.getErrorCode());
        assertArrayEquals(new Object[]{"primary"}, exception.getMessageArgs());
        assertSame(cause, exception.getCause());
    }

    /**
     * 验证空注册表允许门面启动，直到查询客户端时才报告模型缺失。
     */
    @Test
    @DisplayName("空注册表延迟报告对话模型缺失")
    void shouldDeferMissingChatModelUntilClientLookup() {
        AiTemplate template = template(Map.of(), Map.of(), null, List.of());

        AiException exception = assertThrows(AiException.class, template::chatClient);

        assertSame(AiErrorCode.CHAT_MODEL_NOT_FOUND, exception.getErrorCode());
        assertArrayEquals(new Object[]{"默认模型"}, exception.getMessageArgs());
        assertTrue(template.chatModelNames().isEmpty());
    }

    /**
     * 验证同一模型实例绑定多个 Bean 名称时，默认客户端仍按配置名称准确选择。
     */
    @Test
    @DisplayName("共享模型实例按默认 Bean 名称准确选择客户端")
    void shouldSelectDefaultClientByNameWhenModelInstanceIsShared() {
        ChatModel sharedModel = new TestChatModel("共享回复");
        AiTemplate template = template(
                Map.of("primary", sharedModel, "secondary", sharedModel),
                Map.of(),
                "primary",
                List.of());

        assertSame(template.chatClient("primary"), template.chatClient());
        assertNotSame(template.chatClient("secondary"), template.chatClient());
    }

    /**
     * 创建测试用 AI 调用门面。
     *
     * @param chatModels 对话模型映射
     * @param embeddingModels 嵌入模型映射
     * @param defaultChatModel 默认对话模型名称
     * @param customizers 客户端定制器列表
     * @return 测试用 AI 调用门面
     */
    private static AiTemplate template(
            Map<String, ChatModel> chatModels,
            Map<String, EmbeddingModel> embeddingModels,
            String defaultChatModel,
            List<AiChatClientCustomizer> customizers) {
        AiModelRegistry registry = new AiModelRegistry(
                chatModels,
                embeddingModels,
                defaultChatModel,
                null);
        return new AiTemplate(registry, customizers);
    }

    /**
     * 返回固定文本并记录最近提示词的测试对话模型。
     */
    private static final class TestChatModel implements ChatModel {

        /** 固定响应文本。 */
        private final String responseText;

        /** 最近一次收到的提示词。 */
        private Prompt lastPrompt;

        /**
         * 创建测试对话模型。
         *
         * @param responseText 固定响应文本
         */
        private TestChatModel(String responseText) {
            this.responseText = responseText;
        }

        /**
         * 返回固定的 Spring AI 原生响应。
         *
         * @param prompt 对话提示词
         * @return 固定的原生响应
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage(responseText))));
        }
    }

    /**
     * 返回固定向量的测试嵌入模型。
     */
    private static final class TestEmbeddingModel implements EmbeddingModel {

        /**
         * 返回固定嵌入响应。
         *
         * @param request 嵌入请求
         * @return 固定嵌入响应
         */
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(List.of(new Embedding(new float[]{1.0F}, 0)));
        }

        /**
         * 返回固定嵌入向量。
         *
         * @param document 待嵌入文档
         * @return 固定嵌入向量
         */
        @Override
        public float[] embed(Document document) {
            return new float[]{1.0F};
        }
    }

    /**
     * 可排序的测试客户端定制器。
     */
    private static final class OrderedCustomizer implements AiChatClientCustomizer, Ordered {

        /** Spring 排序值。 */
        private final int order;

        /** 当前定制器标识。 */
        private final String marker;

        /** 共享调用顺序记录。 */
        private final List<String> invocationOrder;

        /**
         * 创建可排序测试定制器。
         *
         * @param order Spring 排序值
         * @param marker 当前定制器标识
         * @param invocationOrder 共享调用顺序记录
         */
        private OrderedCustomizer(int order, String marker, List<String> invocationOrder) {
            this.order = order;
            this.marker = marker;
            this.invocationOrder = invocationOrder;
        }

        /**
         * 记录模型名称和调用顺序。
         *
         * @param modelName 对话模型 Bean 名称
         * @param builder 当前模型专属客户端构建器
         */
        @Override
        public void customize(String modelName, ChatClient.Builder builder) {
            invocationOrder.add(marker + ":" + modelName);
        }

        /**
         * 返回 Spring 排序值。
         *
         * @return Spring 排序值
         */
        @Override
        public int getOrder() {
            return order;
        }
    }
}
