package com.github.leyland.letool.ai.config;

import com.github.leyland.letool.ai.core.AiChatClientCustomizer;
import com.github.leyland.letool.ai.core.AiModelRegistry;
import com.github.leyland.letool.ai.core.AiTemplate;
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
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiAutoConfiguration} 生产化自动配置行为测试。
 */
@DisplayName("AI 自动配置测试")
class AiAutoConfigurationTest {

    /** 基础自动配置上下文运行器。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class));

    /**
     * 验证用户尚未提供模型时应用仍可启动，并注册空模型门面。
     */
    @Test
    @DisplayName("无模型时默认启动并注册门面")
    void shouldStartWithoutModelsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiProperties.class);
            assertThat(context).hasSingleBean(AiModelRegistry.class);
            assertThat(context).hasSingleBean(AiTemplate.class);
            assertThat(context.getBean(AiModelRegistry.class).chatModelNames()).isEmpty();
            assertThat(context.getBean(AiModelRegistry.class).embeddingModelNames()).isEmpty();
        });
    }

    /**
     * 验证显式关闭模块后不注册配置属性和运行时门面。
     */
    @Test
    @DisplayName("关闭模块后不注册任何 AI 门面")
    void shouldNotCreateBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.ai.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AiProperties.class);
                    assertThat(context).doesNotHaveBean(AiModelRegistry.class);
                    assertThat(context).doesNotHaveBean(AiTemplate.class);
                });
    }

    /**
     * 验证单个对话模型会自动成为默认模型。
     */
    @Test
    @DisplayName("单个 ChatModel 自动成为默认模型")
    void shouldSelectSingleChatModelAsDefault() {
        contextRunner
                .withUserConfiguration(SingleChatModelConfiguration.class)
                .run(context -> {
                    AiModelRegistry registry = context.getBean(AiModelRegistry.class);
                    assertThat(registry.chatModel()).isSameAs(context.getBean("primaryChat"));
                    assertThat(context.getBean(AiTemplate.class).chatClient()).isNotNull();
                });
    }

    /**
     * 验证多个对话模型会按显式配置的 Bean 名称选择默认模型。
     */
    @Test
    @DisplayName("多个 ChatModel 按显式名称选择默认模型")
    void shouldSelectConfiguredDefaultFromMultipleChatModels() {
        contextRunner
                .withUserConfiguration(MultipleChatModelConfiguration.class)
                .withPropertyValues("letool.ai.default-chat-model=secondaryChat")
                .run(context -> assertThat(context.getBean(AiModelRegistry.class).chatModel())
                        .isSameAs(context.getBean("secondaryChat")));
    }

    /**
     * 验证多个对话模型未配置默认名称时上下文启动失败。
     */
    @Test
    @DisplayName("多个 ChatModel 未指定默认名称时启动失败")
    void shouldFailForAmbiguousChatModels() {
        contextRunner
                .withUserConfiguration(MultipleChatModelConfiguration.class)
                .run(context -> assertConfigurationFailure(context.getStartupFailure()));
    }

    /**
     * 验证对话默认名称不存在时上下文启动失败。
     */
    @Test
    @DisplayName("默认 ChatModel 不存在时启动失败")
    void shouldFailForUnknownDefaultChatModel() {
        contextRunner
                .withUserConfiguration(SingleChatModelConfiguration.class)
                .withPropertyValues("letool.ai.default-chat-model=missing")
                .run(context -> assertConfigurationFailure(context.getStartupFailure()));
    }

    /**
     * 验证单个嵌入模型会自动成为默认模型。
     */
    @Test
    @DisplayName("单个 EmbeddingModel 自动成为默认模型")
    void shouldSelectSingleEmbeddingModelAsDefault() {
        contextRunner
                .withUserConfiguration(SingleEmbeddingModelConfiguration.class)
                .run(context -> assertThat(context.getBean(AiModelRegistry.class).embeddingModel())
                        .isSameAs(context.getBean("primaryEmbedding")));
    }

    /**
     * 验证多个嵌入模型会按显式配置的 Bean 名称选择默认模型。
     */
    @Test
    @DisplayName("多个 EmbeddingModel 按显式名称选择默认模型")
    void shouldSelectConfiguredDefaultFromMultipleEmbeddingModels() {
        contextRunner
                .withUserConfiguration(MultipleEmbeddingModelConfiguration.class)
                .withPropertyValues("letool.ai.default-embedding-model=secondaryEmbedding")
                .run(context -> assertThat(context.getBean(AiModelRegistry.class).embeddingModel())
                        .isSameAs(context.getBean("secondaryEmbedding")));
    }

    /**
     * 验证多个嵌入模型未配置默认名称时上下文启动失败。
     */
    @Test
    @DisplayName("多个 EmbeddingModel 未指定默认名称时启动失败")
    void shouldFailForAmbiguousEmbeddingModels() {
        contextRunner
                .withUserConfiguration(MultipleEmbeddingModelConfiguration.class)
                .run(context -> assertConfigurationFailure(context.getStartupFailure()));
    }

    /**
     * 验证嵌入默认名称不存在时上下文启动失败。
     */
    @Test
    @DisplayName("默认 EmbeddingModel 不存在时启动失败")
    void shouldFailForUnknownDefaultEmbeddingModel() {
        contextRunner
                .withUserConfiguration(SingleEmbeddingModelConfiguration.class)
                .withPropertyValues("letool.ai.default-embedding-model=missing")
                .run(context -> assertConfigurationFailure(context.getStartupFailure()));
    }

    /**
     * 验证用户提供模型注册表时自动配置不会创建重复实例。
     */
    @Test
    @DisplayName("用户自定义 AiModelRegistry 时自动退让")
    void shouldBackOffForUserRegistry() {
        contextRunner
                .withUserConfiguration(UserRegistryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AiModelRegistry.class);
                    assertThat(context.getBean(AiModelRegistry.class))
                            .isSameAs(context.getBean("userRegistry"));
                });
    }

    /**
     * 验证用户提供调用门面时自动配置不会创建重复实例。
     */
    @Test
    @DisplayName("用户自定义 AiTemplate 时自动退让")
    void shouldBackOffForUserTemplate() {
        contextRunner
                .withUserConfiguration(UserTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AiTemplate.class);
                    assertThat(context.getBean(AiTemplate.class))
                            .isSameAs(context.getBean("userTemplate"));
                });
    }

    /**
     * 验证自动配置按 Spring 顺序收集并应用多个客户端定制器。
     */
    @Test
    @DisplayName("多个客户端定制器按 Spring 顺序应用")
    void shouldApplyMultipleCustomizersInOrder() {
        contextRunner
                .withUserConfiguration(
                        SingleChatModelConfiguration.class,
                        OrderedCustomizersConfiguration.class)
                .run(context -> assertThat(context.getBean(CustomizerInvocations.class).values)
                        .containsExactly("early:primaryChat", "late:primaryChat"));
    }

    /**
     * 验证模块不再自动注册任何自研 Provider、HTTP、RAG 或向量存储组件。
     */
    @Test
    @DisplayName("不注册自研 Provider HTTP RAG 和向量存储 Bean")
    void shouldNotCreateLegacyAiInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("aiHttpTransport");
            assertThat(context).doesNotHaveBean("deepSeekProvider");
            assertThat(context).doesNotHaveBean("qwenProvider");
            assertThat(context).doesNotHaveBean("vectorStore");
            assertThat(context).doesNotHaveBean("ragService");
            assertThat(context).doesNotHaveBean("embeddingService");
            assertThat(context).doesNotHaveBean("chatSession");
            assertThat(context).doesNotHaveBean("promptTemplate");
            assertThat(context).doesNotHaveBean("functionCallHandler");
        });
    }

    /**
     * 断言启动失败根因属于结构化 AI 配置异常。
     *
     * @param startupFailure 上下文启动异常
     */
    private static void assertConfigurationFailure(Throwable startupFailure) {
        assertThat(startupFailure).isNotNull();
        assertThat(startupFailure).hasRootCauseInstanceOf(AiException.class);
        Throwable rootCause = startupFailure;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertThat(((AiException) rootCause).getErrorCode())
                .isSameAs(AiErrorCode.CONFIGURATION_INVALID);
    }

    /**
     * 提供单个对话模型的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class SingleChatModelConfiguration {

        /**
         * 创建主对话模型。
         *
         * @return 主对话模型
         */
        @Bean
        ChatModel primaryChat() {
            return new TestChatModel("主模型");
        }
    }

    /**
     * 提供两个对话模型的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class MultipleChatModelConfiguration {

        /**
         * 创建主对话模型。
         *
         * @return 主对话模型
         */
        @Bean
        ChatModel primaryChat() {
            return new TestChatModel("主模型");
        }

        /**
         * 创建备用对话模型。
         *
         * @return 备用对话模型
         */
        @Bean
        ChatModel secondaryChat() {
            return new TestChatModel("备用模型");
        }
    }

    /**
     * 提供单个嵌入模型的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class SingleEmbeddingModelConfiguration {

        /**
         * 创建主嵌入模型。
         *
         * @return 主嵌入模型
         */
        @Bean
        EmbeddingModel primaryEmbedding() {
            return new TestEmbeddingModel();
        }
    }

    /**
     * 提供两个嵌入模型的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class MultipleEmbeddingModelConfiguration {

        /**
         * 创建主嵌入模型。
         *
         * @return 主嵌入模型
         */
        @Bean
        EmbeddingModel primaryEmbedding() {
            return new TestEmbeddingModel();
        }

        /**
         * 创建备用嵌入模型。
         *
         * @return 备用嵌入模型
         */
        @Bean
        EmbeddingModel secondaryEmbedding() {
            return new TestEmbeddingModel();
        }
    }

    /**
     * 提供用户自定义注册表的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserRegistryConfiguration {

        /**
         * 创建用户自定义模型注册表。
         *
         * @return 用户自定义模型注册表
         */
        @Bean
        AiModelRegistry userRegistry() {
            return new AiModelRegistry(Map.of(), Map.of(), null, null);
        }
    }

    /**
     * 提供用户自定义调用门面的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserTemplateConfiguration {

        /**
         * 创建用户自定义 AI 调用门面。
         *
         * @return 用户自定义 AI 调用门面
         */
        @Bean
        AiTemplate userTemplate() {
            AiModelRegistry registry = new AiModelRegistry(Map.of(), Map.of(), null, null);
            return new AiTemplate(registry, List.of());
        }
    }

    /**
     * 提供两个可排序定制器的测试配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class OrderedCustomizersConfiguration {

        /**
         * 创建共享调用记录。
         *
         * @return 共享调用记录
         */
        @Bean
        CustomizerInvocations customizerInvocations() {
            return new CustomizerInvocations();
        }

        /**
         * 创建较晚执行的定制器。
         *
         * @param invocations 共享调用记录
         * @return 较晚执行的定制器
         */
        @Bean
        AiChatClientCustomizer lateCustomizer(CustomizerInvocations invocations) {
            return new RecordingCustomizer(20, "late", invocations);
        }

        /**
         * 创建较早执行的定制器。
         *
         * @param invocations 共享调用记录
         * @return 较早执行的定制器
         */
        @Bean
        AiChatClientCustomizer earlyCustomizer(CustomizerInvocations invocations) {
            return new RecordingCustomizer(-20, "early", invocations);
        }
    }

    /**
     * 记录定制器调用顺序的可变测试夹具。
     */
    static final class CustomizerInvocations {

        /** 调用顺序记录。 */
        private final List<String> values = new ArrayList<>();
    }

    /**
     * 记录调用顺序的可排序客户端定制器。
     */
    static final class RecordingCustomizer implements AiChatClientCustomizer, Ordered {

        /** Spring 排序值。 */
        private final int order;

        /** 当前定制器标识。 */
        private final String marker;

        /** 共享调用记录。 */
        private final CustomizerInvocations invocations;

        /**
         * 创建记录型客户端定制器。
         *
         * @param order Spring 排序值
         * @param marker 当前定制器标识
         * @param invocations 共享调用记录
         */
        RecordingCustomizer(int order, String marker, CustomizerInvocations invocations) {
            this.order = order;
            this.marker = marker;
            this.invocations = invocations;
        }

        /**
         * 记录当前模型名称。
         *
         * @param modelName 对话模型 Bean 名称
         * @param builder 当前模型专属客户端构建器
         */
        @Override
        public void customize(String modelName, ChatClient.Builder builder) {
            invocations.values.add(marker + ":" + modelName);
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

    /**
     * 返回固定文本的测试对话模型。
     */
    static final class TestChatModel implements ChatModel {

        /** 固定响应文本。 */
        private final String responseText;

        /**
         * 创建测试对话模型。
         *
         * @param responseText 固定响应文本
         */
        TestChatModel(String responseText) {
            this.responseText = responseText;
        }

        /**
         * 返回固定 Spring AI 响应。
         *
         * @param prompt 对话提示词
         * @return 固定 Spring AI 响应
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage(responseText))));
        }
    }

    /**
     * 返回固定向量的测试嵌入模型。
     */
    static final class TestEmbeddingModel implements EmbeddingModel {

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
}
