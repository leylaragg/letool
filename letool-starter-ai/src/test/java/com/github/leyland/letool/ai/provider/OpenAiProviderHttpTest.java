package com.github.leyland.letool.ai.provider;

import com.github.leyland.letool.ai.config.AiProperties;
import com.github.leyland.letool.ai.core.ChatRequest;
import com.github.leyland.letool.ai.core.ChatResponse;
import com.github.leyland.letool.ai.exception.AiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容 provider 的 HTTP 生产化能力测试。
 *
 * <p>使用本地 HTTP server 模拟上游服务，覆盖重试、错误码解析、密钥脱敏和读超时。</p>
 */
class OpenAiProviderHttpTest {

    /**
     * 验证 5xx 这类临时错误会按配置重试，最终成功响应会被正常解析。
     */
    @Test
    void chatShouldRetryRetryableHttpErrors() throws Exception {
        try (AiHttpServer server = AiHttpServer.start()) {
            server.enqueue(500, "{\"error\":{\"message\":\"temporary-1\",\"type\":\"server_error\",\"code\":\"server_error\"}}");
            server.enqueue(502, "{\"error\":{\"message\":\"temporary-2\",\"type\":\"server_error\",\"code\":\"bad_gateway\"}}");
            server.enqueue(200, chatResponse("hello"));

            OpenAiProvider provider = new OpenAiProvider(config(server.baseUrl(), "sk-test-retry", 2, 0, 3000));
            ChatResponse response = provider.chat(ChatRequest.builder().addUser("hi").build());

            assertEquals("hello", response.getContent());
            assertEquals(3, server.requestCount());
        }
    }

    /**
     * 验证认证错误不会重试，并且异常会携带上游错误 code/type，同时脱敏 API key。
     */
    @Test
    void chatShouldNotRetryAuthErrorsAndShouldRedactApiKey() throws Exception {
        String apiKey = "sk-secret-should-not-leak";
        try (AiHttpServer server = AiHttpServer.start()) {
            server.enqueue(401, "{\"error\":{\"message\":\"bad key " + apiKey
                    + "\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}");

            OpenAiProvider provider = new OpenAiProvider(config(server.baseUrl(), apiKey, 3, 0, 3000));
            AiException ex = assertThrows(AiException.class,
                    () -> provider.chat(ChatRequest.builder().addUser("hi").build()));

            assertEquals(401, ex.getStatusCode());
            assertEquals("invalid_api_key", ex.getErrorCode());
            assertEquals("invalid_request_error", ex.getErrorType());
            assertTrue(ex.isAuthError());
            assertFalse(ex.getMessage().contains(apiKey));
            assertTrue(ex.getMessage().contains("[REDACTED]"));
            assertEquals(1, server.requestCount());
        }
    }

    /**
     * 验证读超时配置会被 HTTP 请求使用，并转换为 AI 模块异常。
     */
    @Test
    void chatShouldRespectReadTimeout() throws Exception {
        try (AiHttpServer server = AiHttpServer.start()) {
            server.enqueueDelayed(200, chatResponse("too-late"), 350);

            OpenAiProvider provider = new OpenAiProvider(config(server.baseUrl(), "sk-test-timeout", 0, 0, 80));
            AiException ex = assertThrows(AiException.class,
                    () -> provider.chat(ChatRequest.builder().addUser("hi").build()));

            assertTrue(ex.getMessage().contains("超时"));
            assertEquals(1, server.requestCount());
        }
    }

    private static AiProperties.OpenAi config(String baseUrl,
                                              String apiKey,
                                              int maxRetries,
                                              long retryBackoffMillis,
                                              int readTimeoutMillis) {
        AiProperties.OpenAi config = new AiProperties.OpenAi();
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        config.setDefaultModel("gpt-test");
        config.setMaxRetries(maxRetries);
        config.setRetryBackoffMillis(retryBackoffMillis);
        config.setConnectTimeoutMillis(500);
        config.setReadTimeoutMillis(readTimeoutMillis);
        return config;
    }

    private static String chatResponse(String content) {
        return "{"
                + "\"id\":\"chatcmpl-test\","
                + "\"model\":\"gpt-test\","
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content
                + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}"
                + "}";
    }

    /**
     * 测试用 OpenAI 兼容 HTTP 服务，按入队顺序返回响应。
     */
    static class AiHttpServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private final Queue<StubResponse> responses = new ArrayDeque<>();
        private final AtomicInteger requestCount = new AtomicInteger();

        private AiHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static AiHttpServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newCachedThreadPool();
            AiHttpServer holder = new AiHttpServer(server, executor);
            server.createContext("/chat/completions", holder::handle);
            server.setExecutor(executor);
            server.start();
            return holder;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int requestCount() {
            return requestCount.get();
        }

        void enqueue(int statusCode, String body) {
            responses.add(new StubResponse(statusCode, body, 0));
        }

        void enqueueDelayed(int statusCode, String body, long delayMillis) {
            responses.add(new StubResponse(statusCode, body, delayMillis));
        }

        private void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            StubResponse response = responses.poll();
            if (response == null) {
                response = new StubResponse(500, "{\"error\":{\"message\":\"no response\"}}", 0);
            }
            if (response.delayMillis > 0) {
                try {
                    Thread.sleep(response.delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(response.statusCode, bytes.length);
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

    record StubResponse(int statusCode, String body, long delayMillis) {
    }
}
