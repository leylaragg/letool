package com.github.leyland.letool.ai.config;

import com.github.leyland.letool.ai.chat.ChatSession;
import com.github.leyland.letool.ai.chat.PromptTemplate;
import com.github.leyland.letool.ai.core.AiProvider;
import com.github.leyland.letool.ai.core.AiTemplate;
import com.github.leyland.letool.ai.embedding.EmbeddingService;
import com.github.leyland.letool.ai.function.FunctionCallHandler;
import com.github.leyland.letool.ai.http.AiHttpRequest;
import com.github.leyland.letool.ai.http.AiHttpResponse;
import com.github.leyland.letool.ai.http.AiHttpTransport;
import com.github.leyland.letool.ai.provider.DeepSeekProvider;
import com.github.leyland.letool.ai.provider.OpenAiProvider;
import com.github.leyland.letool.ai.provider.QwenProvider;
import com.github.leyland.letool.ai.rag.DocumentLoader;
import com.github.leyland.letool.ai.rag.RagService;
import com.github.leyland.letool.ai.rag.TextSplitter;
import com.github.leyland.letool.ai.rag.VectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiAutoConfiguration} 的可替换 HTTP 传输层测试。
 */
class AiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class))
            .withPropertyValues(
                    "spring.main.allow-bean-definition-overriding=false",
                    "letool.ai.default-provider=openai",
                    "letool.ai.openai.api-key=sk-test",
                    "letool.ai.openai.base-url=http://localhost");

    /**
     * 验证 AI starter 默认注册所有本地工具组件和 OpenAI-compatible provider。
     */
    @Test
    void shouldCreateDefaultAiBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiProperties.class);
            assertThat(context).hasSingleBean(AiHttpTransport.class);
            assertThat(context).hasBean("openAiProvider");
            assertThat(context).hasBean("deepSeekProvider");
            assertThat(context).hasBean("qwenProvider");
            assertThat(context.getBean("openAiProvider")).isInstanceOf(OpenAiProvider.class);
            assertThat(context.getBean("deepSeekProvider")).isInstanceOf(DeepSeekProvider.class);
            assertThat(context.getBean("qwenProvider")).isInstanceOf(QwenProvider.class);
            assertThat(context).hasSingleBean(AiTemplate.class);
            assertThat(context).hasSingleBean(ChatSession.class);
            assertThat(context).hasSingleBean(EmbeddingService.class);
            assertThat(context).hasSingleBean(VectorStore.class);
            assertThat(context).hasSingleBean(RagService.class);
            assertThat(context).hasSingleBean(PromptTemplate.class);
            assertThat(context).hasSingleBean(FunctionCallHandler.class);
            assertThat(context).hasSingleBean(DocumentLoader.class);
            assertThat(context).hasSingleBean(TextSplitter.class);
            assertThat(context.getBean(AiTemplate.class).listProviders())
                    .contains("openai", "deepseek", "qwen");
        });
    }

    /**
     * 验证关闭 AI starter 后不会注册 AI 运行时 Bean。
     */
    @Test
    void shouldNotCreateBeansWhenAiDisabled() {
        contextRunner
                .withPropertyValues("letool.ai.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AiProperties.class);
                    assertThat(context).doesNotHaveBean(AiHttpTransport.class);
                    assertThat(context).doesNotHaveBean(AiTemplate.class);
                    assertThat(context).doesNotHaveBean(ChatSession.class);
                    assertThat(context).doesNotHaveBean(EmbeddingService.class);
                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).doesNotHaveBean(RagService.class);
                    assertThat(context).doesNotHaveBean(PromptTemplate.class);
                    assertThat(context).doesNotHaveBean(FunctionCallHandler.class);
                    assertThat(context).doesNotHaveBean(DocumentLoader.class);
                    assertThat(context).doesNotHaveBean(TextSplitter.class);
                });
    }

    /**
     * 验证用户提供 {@link AiHttpTransport} 时，OpenAI provider 会复用该传输层。
     */
    @Test
    void openAiProviderShouldUseCustomHttpTransport() {
        contextRunner.withUserConfiguration(CustomTransportConfiguration.class)
                .run(context -> {
                    AiTemplate aiTemplate = context.getBean(AiTemplate.class);
                    RecordingTransport transport = context.getBean(RecordingTransport.class);

                    String content = aiTemplate.chat()
                            .provider("openai")
                            .user("hi")
                            .execute()
                            .getContent();

                    assertThat(content).isEqualTo("from-transport");
                    assertThat(transport.lastRequest).isNotNull();
                    assertThat(transport.lastRequest.url()).endsWith("/chat/completions");
                    assertThat(transport.lastRequest.headers())
                            .containsEntry("Authorization", "Bearer sk-test")
                            .containsEntry("Accept", "application/json");
                    assertThat(transport.lastRequest.body()).contains("\"messages\"");
                });
    }

    /**
     * 验证业务项目自定义 AI 核心组件时，自动配置会完整回退。
     */
    @Test
    void shouldBackOffWhenUserProvidesAiBeans() {
        contextRunner
                .withUserConfiguration(UserAiBeanConfiguration.class)
                .run(context -> {
                    assertThat(context.getBean(AiHttpTransport.class)).isSameAs(context.getBean("aiHttpTransport"));
                    assertThat(context.getBean("openAiProvider")).isInstanceOf(OpenAiProvider.class);
                    assertThat(context.getBean("deepSeekProvider")).isInstanceOf(DeepSeekProvider.class);
                    assertThat(context.getBean("qwenProvider")).isInstanceOf(QwenProvider.class);
                    assertThat(context.getBean(AiTemplate.class)).isSameAs(context.getBean("aiTemplate"));
                    assertThat(context.getBean(ChatSession.class)).isSameAs(context.getBean("chatSession"));
                    assertThat(context.getBean(EmbeddingService.class)).isSameAs(context.getBean("embeddingService"));
                    assertThat(context.getBean(VectorStore.class)).isSameAs(context.getBean("vectorStore"));
                    assertThat(context.getBean(RagService.class)).isSameAs(context.getBean("ragService"));
                    assertThat(context.getBean(PromptTemplate.class)).isSameAs(context.getBean("promptTemplate"));
                    assertThat(context.getBean(FunctionCallHandler.class)).isSameAs(context.getBean("functionCallHandler"));
                    assertThat(context.getBean(DocumentLoader.class)).isSameAs(context.getBean("documentLoader"));
                    assertThat(context.getBean(TextSplitter.class)).isSameAs(context.getBean("textSplitter"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTransportConfiguration {

        /**
         * 测试用传输层，记录 provider 构建出的 HTTP 请求。
         *
         * @return 记录请求的传输层实例
         */
        @Bean
        RecordingTransport recordingTransport() {
            return new RecordingTransport();
        }
    }

    /**
     * 用户侧 AI 组件配置，用于验证 starter 默认 Bean 的可替换边界。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserAiBeanConfiguration {

        /**
         * 自定义 HTTP 传输层。
         *
         * @return 记录请求的测试传输层
         */
        @Bean
        AiHttpTransport aiHttpTransport() {
            return new RecordingTransport();
        }

        /**
         * 自定义 OpenAI provider。
         *
         * @param properties    AI 配置属性
         * @param httpTransport HTTP 传输层
         * @return OpenAI provider 实例
         */
        @Bean
        OpenAiProvider openAiProvider(AiProperties properties, AiHttpTransport httpTransport) {
            return new OpenAiProvider(properties.getOpenai(), httpTransport);
        }

        /**
         * 自定义 DeepSeek provider。
         *
         * @param properties    AI 配置属性
         * @param httpTransport HTTP 传输层
         * @return DeepSeek provider 实例
         */
        @Bean
        DeepSeekProvider deepSeekProvider(AiProperties properties, AiHttpTransport httpTransport) {
            return new DeepSeekProvider(properties.getDeepseek(), httpTransport);
        }

        /**
         * 自定义通义千问 provider。
         *
         * @param properties    AI 配置属性
         * @param httpTransport HTTP 传输层
         * @return 通义千问 provider 实例
         */
        @Bean
        QwenProvider qwenProvider(AiProperties properties, AiHttpTransport httpTransport) {
            return new QwenProvider(properties.getQwen(), httpTransport);
        }

        /**
         * 自定义 AI 调用模板。
         *
         * @param providers  AI provider 列表
         * @param properties AI 配置属性
         * @return AI 调用模板实例
         */
        @Bean
        AiTemplate aiTemplate(List<AiProvider> providers, AiProperties properties) {
            return new AiTemplate(providers, properties);
        }

        /**
         * 自定义对话会话组件。
         *
         * @param aiTemplate AI 调用模板
         * @param properties AI 配置属性
         * @return 对话会话实例
         */
        @Bean
        ChatSession chatSession(AiTemplate aiTemplate, AiProperties properties) {
            return new ChatSession(aiTemplate, properties);
        }

        /**
         * 自定义嵌入服务。
         *
         * @param aiTemplate AI 调用模板
         * @return 嵌入服务实例
         */
        @Bean
        EmbeddingService embeddingService(AiTemplate aiTemplate) {
            return new EmbeddingService(aiTemplate);
        }

        /**
         * 自定义内存向量存储。
         *
         * @param embeddingService 嵌入服务
         * @return 向量存储实例
         */
        @Bean
        VectorStore vectorStore(EmbeddingService embeddingService) {
            return new VectorStore(embeddingService);
        }

        /**
         * 自定义 RAG 服务。
         *
         * @param embeddingService 嵌入服务
         * @param vectorStore      向量存储
         * @param aiTemplate       AI 调用模板
         * @return RAG 服务实例
         */
        @Bean
        RagService ragService(EmbeddingService embeddingService, VectorStore vectorStore, AiTemplate aiTemplate) {
            return new RagService(embeddingService, vectorStore, aiTemplate);
        }

        /**
         * 自定义提示词模板引擎。
         *
         * @return 提示词模板实例
         */
        @Bean
        PromptTemplate promptTemplate() {
            return new PromptTemplate();
        }

        /**
         * 自定义函数调用处理器。
         *
         * @return 函数调用处理器实例
         */
        @Bean
        FunctionCallHandler functionCallHandler() {
            return new FunctionCallHandler();
        }

        /**
         * 自定义文档加载器。
         *
         * @return 文档加载器实例
         */
        @Bean
        DocumentLoader documentLoader() {
            return new DocumentLoader();
        }

        /**
         * 自定义文本分块器。
         *
         * @return 文本分块器实例
         */
        @Bean
        TextSplitter textSplitter() {
            return new TextSplitter();
        }
    }

    /**
     * 记录请求并返回固定 OpenAI-compatible 响应的传输层。
     */
    static class RecordingTransport implements AiHttpTransport {

        private AiHttpRequest lastRequest;

        @Override
        public AiHttpResponse post(AiHttpRequest request) {
            this.lastRequest = request;
            return new AiHttpResponse(200, "{"
                    + "\"id\":\"chatcmpl-test\","
                    + "\"model\":\"gpt-test\","
                    + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"from-transport\"},"
                    + "\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}"
                    + "}");
        }

        @Override
        public AiHttpResponse postStream(AiHttpRequest request, Consumer<String> onLine) throws IOException {
            this.lastRequest = request;
            onLine.accept("data: [DONE]");
            return new AiHttpResponse(200, "");
        }
    }
}
