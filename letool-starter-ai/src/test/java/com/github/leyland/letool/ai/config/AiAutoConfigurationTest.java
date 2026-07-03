package com.github.leyland.letool.ai.config;

import com.github.leyland.letool.ai.core.AiTemplate;
import com.github.leyland.letool.ai.http.AiHttpRequest;
import com.github.leyland.letool.ai.http.AiHttpResponse;
import com.github.leyland.letool.ai.http.AiHttpTransport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiAutoConfiguration} 的可替换 HTTP 传输层测试。
 */
class AiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class))
            .withPropertyValues(
                    "letool.ai.default-provider=openai",
                    "letool.ai.openai.api-key=sk-test",
                    "letool.ai.openai.base-url=http://localhost");

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
