package com.github.leyland.letool.ai.core;

import com.github.leyland.letool.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link AiTemplate} 的调用路由测试。
 */
class AiTemplateTest {

    /**
     * 验证流式对话入口会构建完整请求并转发到目标 provider。
     */
    @Test
    void executeStreamShouldRouteRequestToProvider() {
        AiProperties properties = new AiProperties();
        properties.setDefaultProvider("test");
        properties.getChat().setDefaultTemperature(0.4);
        properties.getChat().setDefaultMaxTokens(128);
        properties.getCustomProviders().put("test", providerConfig("test-model"));

        TestProvider provider = new TestProvider();
        AiTemplate template = new AiTemplate(List.of(provider), properties);
        List<String> deltas = new ArrayList<>();

        template.chat()
                .user("hi")
                .executeStream(deltas::add);

        assertEquals(List.of("A", "B"), deltas);
        assertNotNull(provider.lastStreamRequest);
        assertEquals("test", provider.lastStreamRequest.getProvider());
        assertEquals("test-model", provider.lastStreamRequest.getModel());
        assertEquals(0.4, provider.lastStreamRequest.getTemperature(), 0.001);
        assertEquals(128, provider.lastStreamRequest.getMaxTokens());
        assertEquals("hi", provider.lastStreamRequest.getMessages().get(0).getContent());
    }

    private AiProperties.Provider providerConfig(String defaultModel) {
        AiProperties.Provider config = new AiProperties.Provider();
        config.setApiKey("sk-test");
        config.setBaseUrl("http://localhost");
        config.setDefaultModel(defaultModel);
        return config;
    }

    /**
     * 测试用 provider，记录收到的流式请求。
     */
    static class TestProvider implements AiProvider {

        private ChatRequest lastStreamRequest;

        @Override
        public ChatResponse chat(ChatRequest request) {
            return new ChatResponse();
        }

        @Override
        public void chatStream(ChatRequest request, Consumer<String> onDelta) {
            lastStreamRequest = request;
            onDelta.accept("A");
            onDelta.accept("B");
        }

        @Override
        public EmbeddingResponse embedding(EmbeddingRequest request) {
            return new EmbeddingResponse();
        }

        @Override
        public String getProviderName() {
            return "test";
        }

        @Override
        public List<String> getAvailableModels() {
            return List.of("test-model");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
