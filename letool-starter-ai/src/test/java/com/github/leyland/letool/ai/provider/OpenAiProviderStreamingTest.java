package com.github.leyland.letool.ai.provider;

import com.github.leyland.letool.ai.config.AiProperties;
import com.github.leyland.letool.ai.core.ChatRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容 provider 的流式响应测试。
 *
 * <p>使用本地 HTTP server 模拟 text/event-stream，验证 provider 能解析 SSE 增量内容。</p>
 */
class OpenAiProviderStreamingTest {

    /**
     * 验证流式对话会发送 stream=true，并按顺序回调增量文本。
     */
    @Test
    void chatStreamShouldParseSseDeltasInOrder() throws Exception {
        try (StreamingServer server = StreamingServer.start()) {
            OpenAiProvider provider = new OpenAiProvider(config(server.baseUrl()));
            List<String> deltas = new ArrayList<>();

            provider.chatStream(ChatRequest.builder().addUser("hi").build(), deltas::add);

            assertEquals(List.of("你", "好"), deltas);
            assertEquals(1, server.requestCount);
            assertTrue(server.requestBody.contains("\"stream\":true"));
        }
    }

    private static AiProperties.OpenAi config(String baseUrl) {
        AiProperties.OpenAi config = new AiProperties.OpenAi();
        config.setBaseUrl(baseUrl);
        config.setApiKey("sk-stream-test");
        config.setDefaultModel("gpt-test");
        config.setConnectTimeoutMillis(500);
        config.setReadTimeoutMillis(3000);
        config.setMaxRetries(0);
        return config;
    }

    /**
     * 测试用 SSE 服务，返回两个增量 chunk 和 DONE。
     */
    static class StreamingServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private volatile int requestCount;
        private volatile String requestBody;

        private StreamingServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static StreamingServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            StreamingServer holder = new StreamingServer(server, executor);
            server.createContext("/chat/completions", holder::handle);
            server.setExecutor(executor);
            server.start();
            return holder;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            requestCount++;
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = ""
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
                    + "data: [DONE]\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
